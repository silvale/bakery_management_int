package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.ProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductMappingRepository extends JpaRepository<ProductMapping, UUID> {

    /** Tìm mapping theo SKU — dùng khi parse file POS */
    Optional<ProductMapping> findBySkuCode(String skuCode);

    /** Tất cả SKU của 1 master product */
    List<ProductMapping> findAllByProductIdAndIsActiveTrue(UUID productId);

    /** Tìm master product từ SKU code */
    @Query("""
        SELECT pm FROM ProductMapping pm
        JOIN FETCH pm.product
        WHERE pm.skuCode = :skuCode
          AND pm.isActive = TRUE
        """)
    Optional<ProductMapping> findWithProductBySkuCode(@Param("skuCode") String skuCode);

    /** Tất cả SKU sản xuất vào thứ X */
    @Query("""
        SELECT pm FROM ProductMapping pm
        JOIN FETCH pm.product
        WHERE pm.productionDay = :day
          AND pm.isActive = TRUE
        """)
    List<ProductMapping> findByProductionDay(@Param("day") Short day);

    boolean existsBySkuCode(String skuCode);
}
