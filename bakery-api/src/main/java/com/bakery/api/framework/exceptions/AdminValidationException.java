package com.bakery.api.framework.exceptions;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class AdminValidationException extends RuntimeException {

    private final List<Map<String, String>> errors;

    public AdminValidationException(String message) {
        super(message);
        this.errors = List.of(Map.of("message", message));
    }

    public AdminValidationException(List<Map<String, String>> errors) {
        super("Validation failed");
        this.errors = errors;
    }
}
