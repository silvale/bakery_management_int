package com.bakery.framework.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super("Không tìm thấy " + EntityNames.vi(resource));   // 26: tiếng Việt, không in UUID
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
