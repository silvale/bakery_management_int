package com.bakery.common.repository;

import com.bakery.common.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID>, JpaSpecificationExecutor<Ingredient> {

    Optional<Ingredient> findByCode(String code);

    List<Ingredient> findAllByIsActiveTrue();

    boolean existsByCode(String code);
}
