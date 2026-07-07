package com.bakery.api.auth.repositories;

import com.bakery.api.auth.entities.ScreenRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScreenRegistryRepository extends JpaRepository<ScreenRegistry, UUID> {

    Optional<ScreenRegistry> findByCode(String code);

    List<ScreenRegistry> findByModuleOrderBySortOrderAsc(String module);

    List<ScreenRegistry> findAllByOrderBySortOrderAsc();
}
