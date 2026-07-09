/*
 * Copyright© OneEmpower Pte Ltd. All rights reserved.
 *
 * This work contains trade secrets and confidential material of
 * OneEmpower Pte Ltd, and its unauthorised dissemination, use or
 * disclosure in whole or in part  without explicit written
 * permission of OneEmpower Pte Ltd is strictly prohibited.
 */
package com.bakery.framework.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.bakery.framework.security.properties.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(String userId, String username, String role) {
        return buildToken(userId, Map.of("username", username, "role", role), jwtProperties.accessTokenExpireMs());
    }

    public String generateRefreshToken(String userId) {
        return buildToken(userId, Map.of(), jwtProperties.refreshTokenExpireMs());
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private String buildToken(String subject, Map<String, Object> claims, long expireMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMs))
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secretKey().getBytes(StandardCharsets.UTF_8));
    }
}
