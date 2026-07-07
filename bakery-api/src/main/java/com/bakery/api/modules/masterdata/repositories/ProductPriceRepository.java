package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPriceRepository
        extends JpaRepository<ProductPrice, UUID>, JpaSpecificationExecutor<ProductPrice> {

    /**
     * Lookup giá đúng tại ngày X.
     * Dùng để: tính expected_revenue, profit, kiểm tra POS.
     */
    @Query("""
        SELECT pp FROM ProductPrice pp
        WHERE pp.product.id = :productId
          AND pp.effectiveDate <= :targetDate
        ORDER BY pp.effectiveDate DESC
        LIMIT 1
        """)
    Optional<ProductPrice> findActivePrice(
        @Param("productId")  UUID productId,
        @Param("targetDate") LocalDate targetDate
    );

    /** Giá hiện tại mới nhất (không cần ngày). */
    @Query("""
        SELECT pp FROM ProductPrice pp
        WHERE pp.product.id = :productId
        ORDER BY pp.effectiveDate DESC
        LIMIT 1
        """)
    Optional<ProductPrice> findLatestPrice(@Param("productId") UUID productId);

    /** Tất cả lịch sử giá của 1 product, mới nhất trước. */
    List<ProductPrice> findByProductIdOrderByEffectiveDateDesc(UUID productId);

    /** Lấy version max để auto-increment. */
    @Query("SELECT COALESCE(MAX(pp.version), 0) FROM ProductPrice pp WHERE pp.product.id = :productId")
    int findMaxVersion(@Param("productId") UUID productId);
}
