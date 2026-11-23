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
        if (isSuperAdmin(e)) throw new IllegalStateException("Không sửa vai trò Admin");
        // 18/D2: vai trò mặc định đổi được tên và mô tả, không đổi mã (mã gắn với kho mặc định, seed, migration)
        if (e.isSystem()) {
            if (req.code() != null && !req.code().equals(e.getCode())) {
                throw new IllegalStateException("Không đổi mã vai trò mặc định");
            }
        } else {
            e.setCode(req.code());
        }
        e.setName(req.name());
        e.setDescription(req.description());
    }

    @Override
    protected void beforeDelete(UserRole e) {
        requireDeletable(e);
    }

    /** 18/D3: vai trò mặc định (hệ thống, gồm Admin) không xóa được → 422. */
    public static void requireDeletable(UserRole e) {
        if (e.isSystem()) throw new IllegalStateException("Vai trò mặc định không xóa được");
    }

    /** 18/D4: Admin (SUPER_ADMIN) khóa hoàn toàn — không sửa quyền, tên, mã; để Admin không tự khóa mình. */
    public static boolean isSuperAdmin(UserRole e) {
        return AccessScope.SUPER_ADMIN.equalsIgnoreCase(e.getCode());
    }

    /** 18/D1+D4: đổi được bộ quyền của mọi vai trò trừ Admin. */
    public static void requireCanEditPermissions(UserRole e) {
        if (isSuperAdmin(e)) throw new IllegalStateException("Không sửa quyền của vai trò Admin");
    }

    @Override
    protected UserRoleResponse toResponse(UserRole e) {
        return UserRoleResponse.from(e);
    }
}
