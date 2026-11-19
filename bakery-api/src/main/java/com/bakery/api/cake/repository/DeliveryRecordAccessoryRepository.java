package com.bakery.api.cake.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bakery.api.cake.entity.DeliveryRecordAccessory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRecordAccessoryRepository extends JpaRepository<DeliveryRecordAccessory, UUID> {

    boolean existsByCakeDesignId(UUID cakeDesignId);

    /** Phụ kiện của nhiều phiếu giao nhận, kèm mẫu và mặt hàng — một query. */
    @Query("""
            SELECT a FROM DeliveryRecordAccessory a JOIN FETCH a.item LEFT JOIN FETCH a.cakeDesign
            WHERE a.deliveryRecord.id IN :ids""")
    List<DeliveryRecordAccessory> findByDeliveryRecordIds(@Param("ids") Collection<UUID> ids);
}
