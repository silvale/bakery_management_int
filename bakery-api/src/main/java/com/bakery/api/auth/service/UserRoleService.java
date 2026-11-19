package com.bakery.api.auth.service;

import com.bakery.api.auth.dto.UserRoleRequest;
import com.bakery.api.auth.dto.UserRoleResponse;
import com.bakery.api.auth.entity.UserRole;
import com.bakery.api.auth.repository.UserRoleRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserRoleService extends AbstractBakeryAdminService<UserRole, UserRoleRequest, UserRoleResponse> {

    private final UserRoleRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<UserRole> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "UserRole"; }
    @Override protected boolean isAutoApprove() { return true; }

    @Override
    protected UserRole toEntity(UserRoleRequest req) {
        UserRole e = new UserRole();
        e.setCode(req.code());
        e.setName(req.name());
        e.setDescription(req.description());
        return e;
    }

    @Override
    protected void applyUpdate(UserRole e, UserRoleRequest req) {
        requireNotSystem(e);
        e.setCode(req.code());
        e.setName(req.name());
        e.setDescription(req.description());
    }

    @Override
    protected void beforeDelete(UserRole e) {
        requireNotSystem(e);
    }

    /** 11/D1: giai đoạn 1 không sửa/xóa vai trò hệ thống → 422. */
    public static void requireNotSystem(UserRole e) {
        if (e.isSystem()) throw new IllegalStateException("Vai trò hệ thống không sửa được");
    }

    @Override
    protected UserRoleResponse toResponse(UserRole e) {
        return UserRoleResponse.from(e);
    }
}
