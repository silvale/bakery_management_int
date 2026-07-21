package com.bakery.api.auth.dto;

public record UserRoleRequest(
        String code,
        String name,
        String description
) {}
