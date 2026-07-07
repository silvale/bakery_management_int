package com.bakery.api.modules.production.repositories;

import com.bakery.api.modules.production.entities.ProductionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionTemplateRepository extends JpaRepository<ProductionTemplate, UUID> {
    Optional<ProductionTemplate> findByProductId(UUID productId);
    List<ProductionTemplate> findAllByIsActiveTrue();
}
