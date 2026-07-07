package com.bakery.api.modules.partner.repositories;

import com.bakery.api.modules.partner.entities.PurchaseOrder;
import com.bakery.api.framework.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByCode(String code);

    List<PurchaseOrder> findAllBySupplierIdOrderByOrderDateDesc(UUID supplierId);

    List<PurchaseOrder> findAllByPaymentStatusNot(PaymentStatus status);

    List<PurchaseOrder> findAllByOrderDateBetweenOrderByOrderDateDesc(
        LocalDate from, LocalDate to
    );

    /** Tổng công nợ của 1 nhà cung cấp */
    @Query("""
        SELECT COALESCE(SUM(po.totalAmount - po.paidAmount), 0)
        FROM PurchaseOrder po
        WHERE po.supplier.id = :supplierId
          AND po.paymentStatus != 'PAID'
        """)
    BigDecimal sumDebtBySupplierId(@Param("supplierId") UUID supplierId);

    /** Sinh code mới: đếm số đơn trong ngày để tạo sequence */
    @Query("""
        SELECT COUNT(po) FROM PurchaseOrder po
        WHERE po.orderDate = :date
        """)
    long countByOrderDate(@Param("date") LocalDate date);
}
