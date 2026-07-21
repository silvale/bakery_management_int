package com.bakery.api.auth.service;

import java.time.Instant;

import com.bakery.api.auth.dto.LoginRequest;
import com.bakery.api.auth.dto.LoginResponse;
import com.bakery.api.auth.entity.RefreshToken;
import com.bakery.api.auth.entity.UserAccount;
import com.bakery.api.auth.repository.RefreshTokenRepository;
import com.bakery.api.auth.repository.UserAccountRepository;
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

    /**
     * Đăng nhập — trả về access token + refresh token.
     */
    @Transactional
    public LoginResponse login(LoginRequest req) {
        UserAccount account = userAccountRepository.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("Tên đăng nhập hoặc mật khẩu không đúng."));

        if (!passwordEncoder.matches(req.password(), account.getPasswordHash())) {
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

        return new LoginResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getRole() != null ? account.getRole().getId() : null,
                roleCode,
                roleName,
                accessToken,
                refreshTokenStr
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

        return new LoginResponse(
                account.getId(),
                account.getUsername(),
                account.getFullName(),
                account.getRole() != null ? account.getRole().getId() : null,
                roleCode,
                roleName,
                newAccessToken,
                refreshToken
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
        });
    }
}
