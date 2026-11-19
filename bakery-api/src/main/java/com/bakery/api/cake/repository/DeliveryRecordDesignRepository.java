package com.bakery.api.cake.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bakery.api.cake.entity.DeliveryRecordDesign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryRecordDesignRepository extends JpaRepository<DeliveryRecordDesign, UUID> {

    boolean existsByCakeDesignId(UUID cakeDesignId);

    /** Mẫu của mọi phiếu giao nhận có ngày sản xuất = {@code date} (kèm mẫu và phiếu). */
    @Query("""
            SELECT x FROM DeliveryRecordDesign x JOIN FETCH x.cakeDesign JOIN FETCH x.deliveryRecord dr
            WHERE dr.productionRequestLine.productionRequest.productionDate = :date""")
    List<DeliveryRecordDesign> findByProductionDate(@Param("date") java.time.LocalDate date);

    /** Các dòng mẫu của nhiều phiếu giao nhận, kèm mẫu — một query cho cả ngày. */
    @Query("SELECT x FROM DeliveryRecordDesign x JOIN FETCH x.cakeDesign WHERE x.deliveryRecord.id IN :ids")
    List<DeliveryRecordDesign> findByDeliveryRecordIds(@Param("ids") Collection<UUID> ids);
}
