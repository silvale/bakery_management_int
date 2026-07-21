package com.bakery.api.auth.dto;

import java.util.List;

import com.bakery.api.auth.entity.ScreenRegistry;

public record ScreenResponse(
        String code,
        String name,
        List<String> availableActions,
        int sortOrder
) {
    public static ScreenResponse from(ScreenRegistry s) {
        return new ScreenResponse(s.getCode(), s.getName(), s.getAvailableActionList(), s.getSortOrder());
    }
}
