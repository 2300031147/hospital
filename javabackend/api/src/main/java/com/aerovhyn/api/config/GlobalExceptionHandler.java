package com.aerovhyn.api.config;

import com.aerovhyn.common.dto.ErrorResponseDto;
import com.aerovhyn.common.exception.AerovhynException;
import com.aerovhyn.common.exception.CapacityExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AerovhynException.class)
    public ResponseEntity<ErrorResponseDto> handleAerovhynException(AerovhynException ex, HttpServletRequest request) {
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleCapacityExceeded(CapacityExceededException ex, HttpServletRequest request) {
        return buildResponse(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(400, message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(HttpServletRequest request) {
        return buildResponse(403, "Access denied", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentials(HttpServletRequest request) {
        return buildResponse(401, "Invalid credentials", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(400, ex.getMessage(), request);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex, HttpServletRequest request) {
        // Unwrap Jackson ValueInstantiationException to find ValidationException
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (cause instanceof AerovhynException ae) {
                return buildResponse(ae.getStatus(), ae.getMessage(), request);
            }
            cause = cause.getCause();
        }
        return buildResponse(400, "Invalid request body: " + ex.getMostSpecificCause().getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneral(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(500, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponseDto> buildResponse(int status, String message, HttpServletRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null) requestId = UUID.randomUUID().toString();

        ErrorResponseDto error = new ErrorResponseDto(
                Instant.now(),
                status,
                HttpStatus.valueOf(status).getReasonPhrase(),
                message,
                request.getRequestURI(),
                requestId
        );
        return ResponseEntity.status(status).body(error);
    }
}
