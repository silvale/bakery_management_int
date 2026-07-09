package com.bakery.framework.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(
        @NotBlank(message = "Reason is required") String reason) {}
