package com.bakery.api.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RoleRequest(
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Code chỉ gồm chữ hoa, số và dấu _")
    @Size(max = 50)
    String code,

    @NotBlank @Size(max = 100)
    String name,

    String description
) {}
