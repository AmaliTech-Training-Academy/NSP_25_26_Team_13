package com.logstream.exception;

public class ResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ResourceNotFoundException forResource(String resourceName, Long id) {
        return new ResourceNotFoundException(resourceName + " not found with id: " + id);
    }

}