package com.bakery.common.repository;

import com.bakery.common.entity.PurchaseOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findAllByPurchaseOrderId(UUID purchaseOrderId);
}
