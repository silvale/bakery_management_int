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
        e.setCode(req.code());
        e.setName(req.name());
        e.setDescription(req.description());
    }

    @Override
    protected UserRoleResponse toResponse(UserRole e) {
        return UserRoleResponse.from(e);
    }
}
