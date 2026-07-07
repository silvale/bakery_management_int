package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.RecipeLineSemi;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecipeLineSemiRepository extends JpaRepository<RecipeLineSemi, UUID> {

    /** Lấy tất cả nguyên liệu trong 1 mẻ bán thành phẩm. */
    List<RecipeLineSemi> findAllBySemiProductId(UUID semiProductId);

    /** Lấy tất cả semi-product dùng 1 nguyên liệu cụ thể — trigger recalculate khi giá NL thay đổi. */
    @Query("SELECT rls FROM RecipeLineSemi rls WHERE rls.ingredient.id = :ingredientId")
    List<RecipeLineSemi> findAllByIngredientId(@Param("ingredientId") UUID ingredientId);
}
