package com.logstream.exception;

import com.logstream.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = Stream.concat(
                ex.getBindingResult().getFieldErrors().stream()
                        .map(e -> Map.entry(e.getField(), Objects.requireNonNull(e.getDefaultMessage()))),
                ex.getBindingResult().getGlobalErrors().stream()
                        .map(e -> Map.entry(e.getObjectName(), Objects.requireNonNull(e.getDefaultMessage())))
        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (existing, replacement) -> existing));

        log.warn("Validation failure: {}", errors);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder().errors(errors).build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing
                ));

        log.warn("Constraint violation: {}", errors);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder().errors(errors).build());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.builder().message(ex.getMessage()).status(HttpStatus.BAD_REQUEST.value()).build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        log.warn("Access denied in MVC layer on '{}': {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.builder()
                        .message("You do not have permission to access this resource.")
                        .status(HttpStatus.FORBIDDEN.value())
                        .build());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder().message(ex.getMessage()).status(HttpStatus.NOT_FOUND.value()).build());
    }

    @ExceptionHandler(FileProcessingException.class)
    public ResponseEntity<ErrorResponse> handleFileProcessingException(FileProcessingException ex) {
        log.warn("File processing error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder().message(ex.getMessage()).status(HttpStatus.INTERNAL_SERVER_ERROR.value()).build());
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ErrorResponse> handleInvalidFileException(InvalidFileException ex) {
        log.warn("Invalid file: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder().message(ex.getMessage()).status(HttpStatus.BAD_REQUEST.value()).build());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoStaticResource(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .message("Resource not found: " + ex.getMessage())
                        .build());
    }
}