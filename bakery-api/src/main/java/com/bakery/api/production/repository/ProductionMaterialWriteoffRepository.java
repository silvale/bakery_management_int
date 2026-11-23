package com.bakery.api.production.repository;

import java.util.UUID;

import com.bakery.api.production.entity.ProductionMaterialWriteoff;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionMaterialWriteoffRepository extends JpaRepository<ProductionMaterialWriteoff, UUID> {
}
