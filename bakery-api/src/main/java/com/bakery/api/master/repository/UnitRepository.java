/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.repository;

import com.bakery.api.master.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitRepository extends JpaRepository<Unit, String> {
}
