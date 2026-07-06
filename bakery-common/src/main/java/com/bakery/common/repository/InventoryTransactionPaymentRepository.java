package com.bakery.common.repository;

import com.bakery.common.entity.InventoryTransactionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventoryTransactionPaymentRepository extends JpaRepository<InventoryTransactionPayment, UUID> {

    List<InventoryTransactionPayment> findByTransactionIdOrderByPaymentDateAsc(UUID transactionId);

    /**
     * Tổng đã thanh toán cho 1 phiếu — so với total_amount để tính công nợ còn lại.
     */
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0)
        FROM InventoryTransactionPayment p
        WHERE p.transaction.id = :transactionId
        """)
    BigDecimal sumPaidByTransaction(@Param("transactionId") UUID transactionId);
}
