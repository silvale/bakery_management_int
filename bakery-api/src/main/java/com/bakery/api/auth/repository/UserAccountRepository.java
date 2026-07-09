package com.bakery.api.auth.repository;

import java.util.Optional;

import com.bakery.api.auth.entity.UserAccount;
import com.bakery.framework.repository.BaseRepository;

public interface UserAccountRepository extends BaseRepository<UserAccount> {
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);
}
