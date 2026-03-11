package com.logstream.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;


@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        String exceptionName = accessDeniedException.getClass().getSimpleName();
        String username = (request.getUserPrincipal() != null)
                ? request.getUserPrincipal().getName()
                : "anonymous";

        log.warn("Access denied [{}] for user '{}' on '{}': {}",
                exceptionName, username, request.getRequestURI(), accessDeniedException.getMessage());

        String acceptHeader = request.getHeader("Accept");
        boolean isHtmlRequest = acceptHeader != null && acceptHeader.contains("text/html");

        if (isHtmlRequest || request.getRequestURI().startsWith("/")) {
            String message = resolveMessage(accessDeniedException);
            response.sendRedirect(request.getContextPath() + "/access-denied?message=" + 
                    java.net.URLEncoder.encode(message, "UTF-8"));
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", resolveMessage(accessDeniedException));

        MAPPER.writeValue(response.getOutputStream(), body);
    }

    private String resolveMessage(AccessDeniedException ex) {
        if (ex.getCause() instanceof InsufficientAuthenticationException) {
            return "Elevated authentication is required to access this resource.";
        }
        return "You do not have permission to access this resource.";
    }
}