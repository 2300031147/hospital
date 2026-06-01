package com.aerovhyn.api.config;

import com.bucket4j.Bandwidth;
import com.bucket4j.Bucket;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(3)
public class RateLimitFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private Bucket createNewBucket(int capacity, Duration period) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(capacity, period))
                .build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (path.startsWith("/api/auth/token")) {
            Bucket bucket = buckets.computeIfAbsent("auth:" + getClientIp(httpRequest),
                    k -> createNewBucket(5, Duration.ofMinutes(1)));
            if (!bucket.tryConsume(1)) {
                httpResponse.setStatus(429);
                httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded for login\"}");
                return;
            }
        } else if (path.startsWith("/api/route")) {
            Bucket bucket = buckets.computeIfAbsent("route:" + getClientIp(httpRequest),
                    k -> createNewBucket(30, Duration.ofMinutes(1)));
            if (!bucket.tryConsume(1)) {
                httpResponse.setStatus(429);
                httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded for routing\"}");
                return;
            }
        } else if (path.startsWith("/api/classify")) {
            Bucket bucket = buckets.computeIfAbsent("classify:" + getClientIp(httpRequest),
                    k -> createNewBucket(60, Duration.ofMinutes(1)));
            if (!bucket.tryConsume(1)) {
                httpResponse.setStatus(429);
                httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded for classification\"}");
                return;
            }
        } else if (path.startsWith("/api/")) {
            Bucket bucket = buckets.computeIfAbsent("api:" + getClientIp(httpRequest),
                    k -> createNewBucket(100, Duration.ofMinutes(1)));
            if (!bucket.tryConsume(1)) {
                httpResponse.setStatus(429);
                httpResponse.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
