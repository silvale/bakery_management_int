package com.bakery.api.production.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionMaterialRepository extends JpaRepository<ProductionMaterial, UUID> {

    /** Theo thứ tự lấy (cũ → mới): FIFO khi dùng/hao hụt, đảo ngược (LIFO) khi trả lại. */
    List<ProductionMaterial> findByProductionRequestIdOrderByPickedAtAscCreatedAtAsc(UUID productionRequestId);

    /** [số dòng, tổng còn trong bảng tạm] của một phiếu; luôn đúng một hàng. */
    @Query("""
        select count(m), coalesce(sum(m.qtyPicked - m.qtyConsumed - m.qtyReturned - m.qtyWrittenOff), 0)
          from ProductionMaterial m where m.productionRequestId = :requestId
        """)
    List<Object[]> summary(@Param("requestId") UUID requestId);
}
