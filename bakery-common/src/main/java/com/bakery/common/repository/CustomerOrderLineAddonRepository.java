package com.bakery.common.repository;

import com.bakery.common.entity.CustomerOrderLineAddon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerOrderLineAddonRepository extends JpaRepository<CustomerOrderLineAddon, UUID> {

    List<CustomerOrderLineAddon> findAllByLineId(UUID lineId);
}
