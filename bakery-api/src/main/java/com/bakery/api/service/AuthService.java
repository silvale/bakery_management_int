package com.bakery.api.service;

import com.bakery.api.config.JwtProperties;
import com.bakery.api.config.JwtUtil;
import com.bakery.api.dto.auth.LoginRequest;
import com.bakery.api.dto.auth.LoginResponse;
import com.bakery.api.dto.auth.LoginResponse.PermissionEntry;
import com.bakery.api.dto.auth.RefreshRequest;
import com.bakery.api.dto.auth.RefreshResponse;
import com.bakery.common.entity.RefreshToken;
import com.bakery.common.entity.RolePermission;
import com.bakery.common.entity.UserProfile;
import com.bakery.common.repository.RefreshTokenRepository;
import com.bakery.common.repository.RolePermissionRepository;
import com.bakery.common.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserProfileRepository    userProfileRepo;
    private final RefreshTokenRepository   refreshTokenRepo;
    private final RolePermissionRepository permissionRepo;
    private final JwtUtil           jwtUtil;
    private final JwtProperties     jwtProperties;
    private final PasswordEncoder   passwordEncoder;

    // ── Login ──────────────────────────────────────────────────────

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserProfile user = userProfileRepo.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu"));

        if (!user.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Sai tên đăng nhập hoặc mật khẩu");
        }

        return buildLoginResponse(user);
    }

    // ── Refresh ────────────────────────────────────────────────────

    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        String hash = jwtUtil.hashToken(request.refreshToken());

        RefreshToken rt = refreshTokenRepo.findByTokenHashAndIsRevokedFalse(hash)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"));

        if (rt.getExpiresAt().isBefore(OffsetDateTime.now())) {
            rt.setIsRevoked(true);
            refreshTokenRepo.save(rt);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn");
        }

        UserProfile user = rt.getUser();
        if (!user.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản đã bị khóa");
        }

        String newAccessToken = jwtUtil.generateAccessToken(
                user.getUsername(), user.getId(), user.getRole().getCode());

        return new RefreshResponse(
                newAccessToken,
                (long) jwtProperties.getAccessTokenExpiryMinutes() * 60
        );
    }

    // ── Logout ─────────────────────────────────────────────────────

    @Transactional
    public void logout(String username) {
        userProfileRepo.findByUsername(username).ifPresent(user -> {
            refreshTokenRepo.revokeAllByUserId(user.getId());
            log.info("Logged out user: {}", username);
        });
    }

    // ── Helper ─────────────────────────────────────────────────────

    private LoginResponse buildLoginResponse(UserProfile user) {
        String rawRefreshToken = jwtUtil.generateRefreshTokenRaw();
        String tokenHash = jwtUtil.hashToken(rawRefreshToken);

        // Revoke refresh tokens cũ (chỉ giữ 1 session)
        refreshTokenRepo.revokeAllByUserId(user.getId());

        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusDays(jwtProperties.getRefreshTokenExpiryDays()))
                .isRevoked(false)
                .build();
        refreshTokenRepo.save(rt);

        // branchId = null cho SUPER_ADMIN/KHO_TRUONG (không giới hạn scope)
        UUID branchId = user.getBranch() != null ? user.getBranch().getId() : null;
        String accessToken = jwtUtil.generateAccessToken(
                user.getUsername(), user.getId(), user.getRole().getCode(), branchId);

        // Load permission matrix cho role của user
        Map<String, PermissionEntry> permissions = loadPermissions(user);

        return new LoginResponse(
                accessToken,
                rawRefreshToken,
                (long) jwtProperties.getAccessTokenExpiryMinutes() * 60,
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getRole().getCode(),
                user.getRole().getName(),
                permissions
        );
    }

    /**
     * Tải ma trận quyền của user theo role.
     * Key = screenCode (VD: "SCREEN_GOODS_TRANSFER")
     * Value = PermissionEntry(view, create, update, delete, approve)
     */
    private Map<String, PermissionEntry> loadPermissions(UserProfile user) {
        Map<String, PermissionEntry> map = new LinkedHashMap<>();

        permissionRepo.findByRoleIdWithScreen(user.getRole().getId())
                .forEach((RolePermission rp) -> map.put(
                        rp.getScreen().getCode(),
                        new PermissionEntry(
                                rp.getCanView(),
                                rp.getCanCreate(),
                                rp.getCanEdit(),    // can_edit ở DB → update ở JSON
                                rp.getCanDelete(),
                                rp.getCanApprove()
                        )
                ));

        return map;
    }
}
