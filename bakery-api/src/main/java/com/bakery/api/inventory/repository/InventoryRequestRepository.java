package com.bakery.api.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.repository.BaseRepository;

public interface InventoryRequestRepository extends BaseRepository<InventoryRequest> {
    Optional<InventoryRequest> findByCode(String code);
    List<InventoryRequest> findByRequestTypeAndRequestDate(InventoryRequestType type, LocalDate date);
    long countByCodeStartingWith(String codePrefix);
}
