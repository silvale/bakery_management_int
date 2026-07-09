package com.bakery.api.recipe.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.recipe.entity.Recipe;
import com.bakery.framework.repository.BaseRepository;

public interface RecipeRepository extends BaseRepository<Recipe> {
    List<Recipe> findByProductId(UUID productId);
    List<Recipe> findBySemiProductId(UUID semiProductId);
    Optional<Recipe> findByProductIdAndActiveTrue(UUID productId);
    Optional<Recipe> findBySemiProductIdAndActiveTrue(UUID semiProductId);
}
