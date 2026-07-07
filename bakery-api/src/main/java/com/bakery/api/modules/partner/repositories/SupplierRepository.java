package com.bakery.api.modules.partner.repositories;

import com.bakery.api.modules.partner.entities.Supplier;
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
