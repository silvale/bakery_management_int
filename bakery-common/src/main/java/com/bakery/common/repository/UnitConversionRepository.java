package com.bakery.common.repository;

import com.bakery.common.entity.UnitConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitConversionRepository extends JpaRepository<UnitConversion, UUID> {

    List<UnitConversion> findAllByIngredientIdAndIsActiveTrue(UUID ingredientId);

    Optional<UnitConversion> findByIngredientIdAndPurchaseUnit(UUID ingredientId, String purchaseUnit);

    Optional<UnitConversion> findByIngredientIdAndIsDefaultTrue(UUID ingredientId);
}
