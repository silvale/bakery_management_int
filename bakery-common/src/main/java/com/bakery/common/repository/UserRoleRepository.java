package com.bakery.common.repository;

import com.bakery.common.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    Optional<UserRole> findByCode(String code);

    boolean existsByCode(String code);

    List<UserRole> findByIsActiveTrue();
}
