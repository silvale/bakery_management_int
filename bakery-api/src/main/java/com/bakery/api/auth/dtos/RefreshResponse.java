package com.bakery.api.auth.dtos;

public record RefreshResponse(
    String accessToken,
    long accessTokenExpiresInSeconds
) {}
