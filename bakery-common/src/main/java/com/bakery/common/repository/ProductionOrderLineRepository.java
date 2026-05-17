package com.bakery.common.repository;

import com.bakery.common.entity.ProductionOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionOrderLineRepository extends JpaRepository<ProductionOrderLine, UUID> {

    List<ProductionOrderLine> findAllByOrderId(UUID orderId);

    Optional<ProductionOrderLine> findByOrderIdAndProductId(UUID orderId, UUID productId);

    /**
     * Cập nhật qty_actual khi đọc file XuatRa.
     * Dùng @Modifying để tránh load entity không cần thiết.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ProductionOrderLine pol
        SET pol.qtyActual = :qtyActual
        WHERE pol.order.id = :orderId
          AND pol.product.id = :productId
        """)
    int updateQtyActual(
        @Param("orderId")    UUID orderId,
        @Param("productId")  UUID productId,
        @Param("qtyActual")  BigDecimal qtyActual
    );
}
