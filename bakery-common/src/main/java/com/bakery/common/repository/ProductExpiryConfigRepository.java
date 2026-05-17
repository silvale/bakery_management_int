package com.bakery.common.repository;

import com.bakery.common.entity.ProductExpiryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductExpiryConfigRepository extends JpaRepository<ProductExpiryConfig, UUID> {
    Optional<ProductExpiryConfig> findByProductId(UUID productId);
}
