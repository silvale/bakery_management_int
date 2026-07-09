package com.bakery.api.auth.repository;

import java.util.Optional;

import com.bakery.api.auth.entity.UserRole;
import com.bakery.framework.repository.BaseRepository;

public interface UserRoleRepository extends BaseRepository<UserRole> {
    Optional<UserRole> findByCode(String code);
}
