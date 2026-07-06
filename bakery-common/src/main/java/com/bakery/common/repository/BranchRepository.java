package com.bakery.common.repository;

import com.bakery.common.entity.Branch;
import com.bakery.common.entity.enums.BranchType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByCode(String code);

    /** Kho Tổng — isMain=TRUE (PurchaseOrder dùng cái này) */
    Optional<Branch> findByIsMainTrue();

    /** Tìm theo loại kho */
    Optional<Branch> findByBranchType(BranchType branchType);

    boolean existsByCode(String code);
}
