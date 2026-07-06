package com.bakery.api.framework.exception;

import java.util.UUID;

public class AdminEntityNotFoundException extends RuntimeException {

    public AdminEntityNotFoundException(String entityType, UUID id) {
        super("Không tìm thấy " + entityType + " với id: " + id);
    }

    public AdminEntityNotFoundException(String message) {
        super(message);
    }
}
