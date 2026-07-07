package com.bakery.api.modules.sales.repositories;

import com.bakery.api.modules.sales.entities.CustomerOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerOrderLineRepository extends JpaRepository<CustomerOrderLine, UUID> {

    @Query("""
        SELECT l FROM CustomerOrderLine l
        JOIN FETCH l.order o
        JOIN FETCH l.product
        WHERE l.id = :id
        """)
    Optional<CustomerOrderLine> findByIdWithOrder(@Param("id") UUID id);
}
