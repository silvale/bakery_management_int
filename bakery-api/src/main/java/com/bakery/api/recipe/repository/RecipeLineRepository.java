package com.bakery.api.recipe.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.framework.repository.BaseRepository;

public interface RecipeLineRepository extends BaseRepository<RecipeLine> {
    List<RecipeLine> findByRecipeIdOrderBySortOrderAsc(UUID recipeId);
    List<RecipeLine> findByItemId(UUID itemId);
}
