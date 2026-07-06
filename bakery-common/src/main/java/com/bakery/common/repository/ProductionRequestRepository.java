package com.bakery.common.repository;

import com.bakery.common.entity.ProductionRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionRequestRepository extends JpaRepository<ProductionRequest, UUID> {

    Optional<ProductionRequest> findByCode(String code);

    List<ProductionRequest> findAllByPlanIdOrderByCreatedAtAsc(UUID planId);

    List<ProductionRequest> findAllByStatusOrderByCreatedAtDesc(String status);

    List<ProductionRequest> findAllByRequestTypeAndStatusOrderByCreatedAtDesc(String requestType, String status);

    long countByCreatedAtBetween(java.time.OffsetDateTime from, java.time.OffsetDateTime to);
}
