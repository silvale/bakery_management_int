package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.framework.enums.BranchType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {

    Optional<Branch> findByCode(String code);
    Optional<Branch> findByIsMainTrue();
    Optional<Branch> findByBranchType(BranchType branchType);
    List<Branch> findAllByBranchType(BranchType branchType);
    List<Branch> findAllByIsActiveTrue();
    boolean existsByCode(String code);
}
