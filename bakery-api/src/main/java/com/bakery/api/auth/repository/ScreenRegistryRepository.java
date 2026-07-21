package com.bakery.api.auth.repository;

import java.util.List;

import com.bakery.api.auth.entity.ScreenRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreenRegistryRepository extends JpaRepository<ScreenRegistry, String> {
    List<ScreenRegistry> findAllByOrderBySortOrderAsc();
}
