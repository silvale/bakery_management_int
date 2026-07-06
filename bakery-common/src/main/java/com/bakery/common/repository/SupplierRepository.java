package com.bakery.common.repository;

import com.bakery.common.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID>, JpaSpecificationExecutor<Supplier> {

    Optional<Supplier> findByCode(String code);

    List<Supplier> findAllByIsActiveTrue();

    boolean existsByCode(String code);
}
