package com.bakery.framework.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectRequest(
        @NotBlank(message = "Phải nhập lý do") String reason) {}
