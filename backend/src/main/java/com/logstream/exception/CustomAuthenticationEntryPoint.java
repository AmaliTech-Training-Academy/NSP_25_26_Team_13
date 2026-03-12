package com.logstream.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        Throwable root = (authException.getCause() != null)
                ? authException.getCause()
                : authException;

        String exceptionName = root.getClass().getSimpleName();
        String message = resolveMessage(authException, root);

        if (root instanceof AuthenticationServiceException) {
            log.error("Authentication service error on '{}': {}", request.getRequestURI(), root.getMessage(), root);
        } else {
            log.warn("Authentication failure [{}] on '{}': {}", exceptionName, request.getRequestURI(), root.getMessage());
        }

        writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, message);
    }

    private String resolveMessage(AuthenticationException authException, Throwable root) {

        if (root instanceof ExpiredJwtException) {
            return "Token expired. Please log in again.";
        }

        if (root instanceof JwtException) {
            return "The authentication token is invalid.";
        }
        if (root instanceof IllegalArgumentException) {
            return "No authentication token was provided in the request.";
        }

        if (authException instanceof BadCredentialsException) {
            return "Invalid username or password.";
        }

        if (authException instanceof InsufficientAuthenticationException) {
            return "Full authentication is required to access this resource.";
        }
        if (authException instanceof AuthenticationServiceException) {
            return "An internal authentication error occurred. Please try again later.";
        }

        return "Authentication failed. Please check your credentials and try again.";
    }

    private void writeJsonResponse(HttpServletResponse response,
                                   int status,
                                   String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);

        MAPPER.writeValue(response.getOutputStream(), body);
    }
}
