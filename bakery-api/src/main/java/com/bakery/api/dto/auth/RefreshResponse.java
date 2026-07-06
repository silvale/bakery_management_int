package com.bakery.api.dto.auth;

public record RefreshResponse(
    String accessToken,
    long accessTokenExpiresInSeconds
) {}
