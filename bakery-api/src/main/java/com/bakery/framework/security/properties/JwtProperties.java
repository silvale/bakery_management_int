/*
 * Copyright© OneEmpower Pte Ltd. All rights reserved.
 *
 * This work contains trade secrets and confidential material of
 * OneEmpower Pte Ltd, and its unauthorised dissemination, use or
 * disclosure in whole or in part  without explicit written
 * permission of OneEmpower Pte Ltd is strictly prohibited.
 */
package com.bakery.framework.security.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bakery.security.jwt")
public record JwtProperties(String secretKey, long accessTokenExpireMs, long refreshTokenExpireMs) {

    public JwtProperties {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("bakery.security.jwt.secret-key must be configured");
        }
        if (accessTokenExpireMs <= 0) accessTokenExpireMs = 3_600_000L; // 1h default
        if (refreshTokenExpireMs <= 0) refreshTokenExpireMs = 604_800_000L; // 7d default
    }
}
