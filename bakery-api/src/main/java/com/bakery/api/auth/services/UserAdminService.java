package com.bakery.api.auth.services;

import com.bakery.api.auth.dtos.UserRequest;
import com.bakery.api.auth.dtos.UserResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.auth.entities.UserProfile;
import com.bakery.api.auth.entities.UserRole;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.auth.repositories.UserProfileRepository;
import com.bakery.api.auth.repositories.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserProfileRepository userRepo;
    private final UserRoleRepository    roleRepo;
    private final BranchRepository      branchRepo;
    private final PasswordEncoder       passwordEncoder;

    public List<UserResponse> listAll() {
        return userRepo.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse getById(UUID id) {
        return UserResponse.from(findUser(id));
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        validateCreate(request);

        UserRole role   = findRole(request.roleId());
        Branch   branch = request.branchId() != null ? findBranch(request.branchId()) : null;

        UserProfile user = UserProfile.builder()
                .username(request.username())
                .email(request.email())
                .fullName(request.fullName())
                .passwordHash(passwordEncoder.encode(request.password()))
                .isActive(true)
                .role(role)
                .branch(branch)
                .build();

        return UserResponse.from(userRepo.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        UserProfile user = findUser(id);

        // Kiểm tra username trùng nếu đổi
        if (!user.getUsername().equals(request.username())
                && userRepo.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username đã tồn tại: " + request.username());
        }

        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setUpdatedAt(OffsetDateTime.now());

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        if (request.roleId() != null) {
            user.setRole(findRole(request.roleId()));
        }

        // null = bỏ gán branch (SUPER_ADMIN / KHO_TRUONG); non-null = gán branch mới
        if (request.branchId() != null) {
            user.setBranch(findBranch(request.branchId()));
        } else {
            user.setBranch(null);   // explicit null = xóa scope giới hạn
        }

        return UserResponse.from(userRepo.save(user));
    }

    @Transactional
    public void deactivate(UUID id) {
        UserProfile user = findUser(id);
        user.setIsActive(false);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepo.save(user);
    }

    @Transactional
    public void activate(UUID id) {
        UserProfile user = findUser(id);
        user.setIsActive(true);
        user.setUpdatedAt(OffsetDateTime.now());
        userRepo.save(user);
    }

    // ── Internal ───────────────────────────────────────────────────

    private UserProfile findUser(UUID id) {
        return userRepo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User không tồn tại: " + id));
    }

    private UserRole findRole(UUID roleId) {
        return roleRepo.findById(roleId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Role không tồn tại: " + roleId));
    }

    private Branch findBranch(UUID branchId) {
        return branchRepo.findById(branchId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch không tồn tại: " + branchId));
    }

    private void validateCreate(UserRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password không được để trống khi tạo user");
        }
        if (userRepo.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username đã tồn tại: " + request.username());
        }
        if (request.roleId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roleId bắt buộc");
        }
    }
}
