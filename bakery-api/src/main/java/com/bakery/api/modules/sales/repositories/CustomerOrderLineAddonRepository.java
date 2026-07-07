package com.bakery.api.modules.sales.repositories;

import com.bakery.api.modules.sales.entities.CustomerOrderLineAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerOrderLineAddonRepository extends JpaRepository<CustomerOrderLineAddon, UUID> {

    List<CustomerOrderLineAddon> findAllByLineId(UUID lineId);
}
