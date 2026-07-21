package com.bakery.api.auth.service;

import java.util.UUID;

import com.bakery.api.auth.dto.UserAccountRequest;
import com.bakery.api.auth.dto.UserAccountResponse;
import com.bakery.api.auth.entity.UserAccount;
import com.bakery.api.auth.entity.UserRole;
import com.bakery.api.auth.repository.UserAccountRepository;
import com.bakery.api.auth.repository.UserRoleRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserAccountService extends AbstractBakeryAdminService<UserAccount, UserAccountRequest, UserAccountResponse> {

    private final UserAccountRepository repository;
    private final UserRoleRepository roleRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;
    private final PasswordEncoder passwordEncoder;

    @Override protected BaseRepository<UserAccount> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "UserAccount"; }
    @Override protected boolean isAutoApprove() { return true; }

    @Override
    protected UserAccount toEntity(UserAccountRequest req) {
        if (!StringUtils.hasText(req.password())) {
            throw new IllegalArgumentException("Password không được để trống khi tạo tài khoản mới.");
        }
        UserAccount e = new UserAccount();
        e.setUsername(req.username());
        e.setFullName(req.fullName());
        e.setPasswordHash(passwordEncoder.encode(req.password()));
        if (req.roleId() != null) {
            e.setRole(findRole(req.roleId()));
        }
        return e;
    }

    @Override
    protected void applyUpdate(UserAccount e, UserAccountRequest req) {
        e.setUsername(req.username());
        e.setFullName(req.fullName());
        if (StringUtils.hasText(req.password())) {
            e.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        e.setRole(req.roleId() != null ? findRole(req.roleId()) : null);
    }

    @Override
    protected UserAccountResponse toResponse(UserAccount e) {
        return UserAccountResponse.from(e);
    }

    /**
     * Đổi mật khẩu — không cần approval flow.
     */
    @Transactional
    public UserAccountResponse changePassword(UUID userId, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new IllegalArgumentException("Mật khẩu mới không được để trống.");
        }
        UserAccount account = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("UserAccount", userId));
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        return toResponse(repository.save(account));
    }

    private UserRole findRole(UUID roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("UserRole", roleId));
    }
}
