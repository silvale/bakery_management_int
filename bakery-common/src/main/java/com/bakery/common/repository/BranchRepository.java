package com.bakery.common.repository;

import com.bakery.common.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByCode(String code);

    Optional<Branch> findByIsMainTrue();

    boolean existsByCode(String code);
}
