package com.aerovhyn.auth.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long tokenValidityMs;

    public JwtTokenProvider(
            @Value("${aerovhyn.jwt.secret}") String secret,
            @Value("${aerovhyn.jwt.expiration-ms:3600000}") long tokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenValidityMs = tokenValidityMs;
    }

    public String createToken(String username, String role, Long userId, Long ambulanceId, Long hospitalId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + tokenValidityMs);

        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("user_id", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key);

        if (ambulanceId != null) {
            builder.claim("ambulance_id", ambulanceId);
        }
        if (hospitalId != null) {
            builder.claim("hospital_id", hospitalId);
        }

        return builder.compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String getUsername(String token) {
        return getClaims(token).getSubject();
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long getUserId(String token) {
        Object val = getClaims(token).get("user_id");
        return val instanceof Number n ? n.longValue() : null;
    }

    public Long getAmbulanceId(String token) {
        Object val = getClaims(token).get("ambulance_id");
        return val instanceof Number n ? n.longValue() : null;
    }

    public Long getHospitalId(String token) {
        Object val = getClaims(token).get("hospital_id");
        return val instanceof Number n ? n.longValue() : null;
    }
}
