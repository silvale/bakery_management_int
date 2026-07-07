package com.bakery.api.modules.sales.repositories;

import com.bakery.api.modules.sales.entities.CustomerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Optional<CustomerOrder> findByCode(String code);

    List<CustomerOrder> findAllByDeliveryDateOrderByCreatedAtAsc(LocalDate deliveryDate);

    List<CustomerOrder> findAllByDeliveryDateAndStatus(LocalDate deliveryDate, String status);

    List<CustomerOrder> findAllByStatusOrderByDeliveryDateAsc(String status);

    List<CustomerOrder> findAllByPaymentStatusNotOrderByDeliveryDateAsc(String paymentStatus);

    long countByCreatedAtBetween(java.time.OffsetDateTime from, java.time.OffsetDateTime to);

    @Query("""
        SELECT o FROM CustomerOrder o
        LEFT JOIN FETCH o.lines l
        LEFT JOIN FETCH l.product
        LEFT JOIN FETCH o.payments
        WHERE o.id = :id
        """)
    Optional<CustomerOrder> findByIdWithDetails(@Param("id") UUID id);
}
