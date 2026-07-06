package com.bakery.api.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.UUID;

/**
 * Tiện ích tạo / xác minh JWT access token.
 * Claims: sub=username, userId=UUID, role=roleCode, branchId=UUID|null
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtUtil {

    private final JwtProperties jwtProperties;

    // ── Key ────────────────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Generate ───────────────────────────────────────────────────

    /**
     * @param branchId UUID branch của user — null nếu user không bị giới hạn scope
     *                 (SUPER_ADMIN, KHO_TRUONG).
     */
    public String generateAccessToken(String username, UUID userId, String roleCode, UUID branchId) {
        long expiryMs = (long) jwtProperties.getAccessTokenExpiryMinutes() * 60 * 1000;
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        var builder = Jwts.builder()
                .subject(username)
                .claim("userId", userId.toString())
                .claim("role", roleCode)
                .claim("branchId", branchId != null ? branchId.toString() : null);

        return builder
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    /** Overload backward-compatible (không có branch — dùng trong refresh token flow). */
    public String generateAccessToken(String username, UUID userId, String roleCode) {
        return generateAccessToken(username, userId, roleCode, null);
    }

    /** Tạo opaque refresh token (UUID random) và trả về cả raw + hash để lưu DB */
    public String generateRefreshTokenRaw() {
        return UUID.randomUUID().toString();
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Cannot hash token", e);
        }
    }

    // ── Validate ───────────────────────────────────────────────────

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString((String) parseClaims(token).get("userId"));
    }

    public String extractRole(String token) {
        return (String) parseClaims(token).get("role");
    }

    /** Trả về branchId từ JWT claims, hoặc null nếu user không bị giới hạn scope. */
    public UUID extractBranchId(String token) {
        String raw = (String) parseClaims(token).get("branchId");
        return raw != null ? UUID.fromString(raw) : null;
    }
}
