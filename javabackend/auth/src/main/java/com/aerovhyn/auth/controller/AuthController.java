package com.aerovhyn.auth.controller;

import com.aerovhyn.auth.config.JwtTokenProvider;
import com.aerovhyn.auth.service.UserService;
import com.aerovhyn.common.dto.LoginRequestDto;
import com.aerovhyn.common.dto.LoginResponseDto;
import com.aerovhyn.common.dto.UserCreateDto;
import com.aerovhyn.common.dto.UserDto;
import com.aerovhyn.common.exception.UnauthorizedException;
import com.aerovhyn.common.exception.ValidationException;
import com.aerovhyn.domain.entity.UserEntity;
import com.aerovhyn.domain.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final boolean cookieSecure;

    public AuthController(
            JwtTokenProvider tokenProvider,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserService userService,
            StringRedisTemplate redisTemplate,
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${aerovhyn.auth.cookie.secure:false}") boolean cookieSecure) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisHost != null && !redisHost.isEmpty();
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/auth/token")
    public LoginResponseDto login(@RequestBody LoginRequestDto request, HttpServletRequest servletRequest,
                                  HttpServletResponse response) {
        String clientIp = extractClientIp(servletRequest);

        if (redisEnabled) {
            String key = "failed_logins:" + clientIp;
            String countStr = redisTemplate.opsForValue().get(key);
            int failedCount = countStr != null ? Integer.parseInt(countStr) : 0;
            if (failedCount >= 5) {
                throw new UnauthorizedException("Too many failed login attempts. Please try again later.");
            }
        }

        UserEntity user = userRepository.findByUsername(request.username())
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (redisEnabled) {
                String key = "failed_logins:" + clientIp;
                redisTemplate.opsForValue().increment(key);
                redisTemplate.expire(key, 5, TimeUnit.MINUTES);
            }
            throw new UnauthorizedException("Invalid username or password");
        }

        if (redisEnabled) {
            redisTemplate.delete("failed_logins:" + clientIp);
        }

        String ambulanceId = user.getRole().equals("paramedic") ? String.valueOf(user.getAmbulanceId()) : null;

        String token = tokenProvider.createToken(
                user.getUsername(),
                user.getRole(),
                user.getId(),
                user.getAmbulanceId(),
                user.getHospitalId()
        );

        Cookie cookie = new Cookie("access_token", "Bearer " + token);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure);
        cookie.setPath("/");
        cookie.setMaxAge(3600);
        response.addCookie(cookie);

        return new LoginResponseDto(
                token,
                "bearer",
                user.getRole(),
                user.getFullName(),
                user.getUsername(),
                user.getId(),
                user.getAmbulanceId(),
                user.getHospitalId()
        );
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public List<UserDto> getUsers() {
        return userService.getAll();
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public UserDto createUser(@RequestBody UserCreateDto dto) {
        return userService.create(dto);
    }

    @PutMapping("/users/{userId}")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public UserDto updateUser(@PathVariable Long userId, @RequestBody UserCreateDto dto) {
        return userService.update(userId, dto);
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public Map<String, Object> deleteUser(@PathVariable Long userId, jakarta.servlet.http.HttpServletRequest request) {
        // Prevent deleting yourself
        Long currentUserId = request.getAttribute("userId") instanceof Number n ? n.longValue() : null;
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new ValidationException("Cannot delete your own account");
        }
        userService.delete(userId);
        return java.util.Map.of("status", "deleted", "id", userId);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank() && !"unknown".equalsIgnoreCase(xri)) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }

    @PutMapping("/users/{userId}/password")
    @PreAuthorize("hasRole('COMMAND_CENTER')")
    public void resetPassword(@PathVariable Long userId, @RequestBody java.util.Map<String, String> body) {
        String newPassword = body.get("new_password");
        if (newPassword == null || newPassword.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
        userService.changePassword(userId, newPassword);
    }
}
