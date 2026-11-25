package com.bakery.api.auth.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;


import com.bakery.api.auth.dto.AuthProfile;
import com.bakery.api.auth.dto.LoginRequest;
import com.bakery.api.auth.dto.LoginResponse;
import com.bakery.api.auth.dto.RefInfo;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.api.auth.repository.RolePermissionRepository;
import com.bakery.api.auth.repository.ScreenRegistryRepository;
import com.bakery.api.auth.entity.RefreshToken;
import com.bakery.api.auth.entity.UserAccount;
import com.bakery.api.auth.repository.RefreshTokenRepository;
import com.bakery.api.auth.repository.UserAccountRepository;
import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.CommandRequest;
import com.bakery.framework.entity.CommandStatus;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.JwtTokenService;
import com.bakery.framework.security.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final CommandRequestRepository commandRequestRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ScreenRegistryRepository screenRegistryRepository;

    /**
     * Đăng nhập — trả về access token + refresh token.
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        UserAccount account = userAccountRepository.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng."));

        if (!passwordEncoder.matches(req.password(), account.getPasswordHash())) {
            // Log failed login attempt
            activityLog(CommandAction.LOGIN, req.username(), null, "FAIL",
                    "Sai mật khẩu cho tài khoản: " + req.username(), CommandStatus.FAILED);
            throw new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng.");
        }

        String roleCode = account.getRole() != null ? account.getRole().getCode() : "USER";
        String roleName = account.getRole() != null ? account.getRole().getName() : "User";

        String accessToken = jwtTokenService.generateAccessToken(
                account.getId().toString(), account.getUsername(), roleCode);
        String refreshTokenStr = jwtTokenService.generateRefreshToken(account.getId().toString());

        // Revoke cũ, lưu mới
        refreshTokenRepository.revokeAllByUserId(account.getId());
        RefreshToken rt = new RefreshToken();
        rt.setUserId(account.getId());
        rt.setToken(refreshTokenStr);
        rt.setExpiresAt(Instant.now().plusMillis(jwtProperties.refreshTokenExpireMs()));
        refreshTokenRepository.save(rt);

        log.info("Login: user={} role={}", account.getUsername(), roleCode);
        activityLog(CommandAction.LOGIN, account.getUsername(), account.getId().toString(),
                account.getUsername() + " (" + roleName + ")", null, CommandStatus.SUCCESS);

        return LoginResponse.of(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getRole() != null ? account.getRole().getId() : null,
                roleCode,
                roleName,
                accessToken,
                refreshTokenStr,
                profileOf(account)
        );
    }

    /**
     * Refresh access token bằng refresh token.
     */
    @Transactional
    public LoginResponse refresh(String refreshToken) {
        RefreshToken rt = refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token không hợp lệ hoặc đã hết hạn."));

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            throw new IllegalArgumentException("Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }

        UserAccount account = userAccountRepository.findById(rt.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại."));

        String roleCode = account.getRole() != null ? account.getRole().getCode() : "USER";
        String roleName = account.getRole() != null ? account.getRole().getName() : "User";
        String newAccessToken = jwtTokenService.generateAccessToken(
                account.getId().toString(), account.getUsername(), roleCode);

        return LoginResponse.of(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getRole() != null ? account.getRole().getId() : null,
                roleCode,
                roleName,
                newAccessToken,
                refreshToken,
                profileOf(account)
        );
    }

    /**
     * Đăng xuất — revoke refresh token của user.
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenAndRevokedFalse(refreshToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Logout: userId={}", rt.getUserId());
            // Resolve username for log
            userAccountRepository.findById(rt.getUserId()).ifPresent(account ->
                activityLog(CommandAction.LOGOUT, account.getUsername(), account.getId().toString(),
                        account.getUsername(), null, CommandStatus.SUCCESS)
            );
        });
    }

    /** {@code GET /auth/me}: hồ sơ phân quyền của user trong token. */
    @Transactional(readOnly = true)
    public AuthProfile me(UUID userId) {
        UserAccount account = userAccountRepository.findWithScopeById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại."));
        return profileOf(account);
    }

    /** Kho được gán, NCC xem giá, permissionMap (Admin: mọi screen × mọi action đã đăng ký). */
    private AuthProfile profileOf(UserAccount a) {
        // load lại kèm kho/NCC (login/refresh nạp account không fetch sẵn)
        UserAccount u = userAccountRepository.findWithScopeById(a.getId()).orElse(a);
        String roleCode = u.getRole() != null ? u.getRole().getCode() : "USER";
        boolean admin = AccessScope.SUPER_ADMIN.equalsIgnoreCase(roleCode);
        Map<String, List<String>> perms = new TreeMap<>();
        if (admin) {
            screenRegistryRepository.findAllByOrderBySortOrderAsc()
                    .forEach(s -> perms.put(s.getCode(), s.getAvailableActionList()));
        } else if (u.getRole() != null) {
            rolePermissionRepository.findByIdRoleId(u.getRole().getId()).forEach(p ->
                    perms.computeIfAbsent(p.getId().getScreenCode(), k -> new ArrayList<>()).add(p.getId().getActionCode()));
            perms.values().forEach(Collections::sort);
        }
        List<RefInfo> warehouses = (admin ? u.getWarehouses() : u.getWarehouses()).stream()
                .sorted(Comparator.comparing(Warehouse::getCode))
                .map(w -> new RefInfo(w.getId(), w.getCode(), w.getName(),
                        w.getKind() != null ? w.getKind().name() : null))
                .toList();
        List<UUID> suppliers = u.getPriceSuppliers().stream().map(Supplier::getId).toList();
        return new AuthProfile(u.getId(), u.getUsername(), u.getFullName(),
                u.getRole() != null ? new ReferenceValue(roleCode, u.getRole().getName()) : null,
                admin, warehouses, suppliers, perms);
    }

    // ── Internal helper ───────────────────────────────────────────

    private void activityLog(CommandAction action, String actorName, String actorId,
                             String entityLabel, String note, CommandStatus status) {
        try {
            commandRequestRepository.save(CommandRequest.builder()
                    .entityName("Auth")
                    .action(action)
                    .actor(actorId)
                    .actorName(actorName)
                    .entityLabel(entityLabel)
                    .note(note)
                    .status(status)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception ignored) {}
    }
}
