package com.bakery.api.modules.partner.repositories;

import com.bakery.api.modules.partner.entities.SupplierReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierReturnRepository extends JpaRepository<SupplierReturn, UUID> {
    Optional<SupplierReturn> findByCode(String code);
    List<SupplierReturn> findAllByStatusOrderByCreatedAtDesc(String status);
    List<SupplierReturn> findAllBySupplierIdOrderByCreatedAtDesc(UUID supplierId);
    List<SupplierReturn> findAllBySupplierIdAndStatusOrderByCreatedAtDesc(UUID supplierId, String status);
    long countByCodeStartingWith(String prefix);
}
