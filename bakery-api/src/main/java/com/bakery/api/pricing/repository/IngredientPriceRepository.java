package com.bakery.api.pricing.repository;

import java.util.List;
import java.util.UUID;

import com.bakery.api.pricing.entity.IngredientPrice;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IngredientPriceRepository extends BaseRepository<IngredientPrice> {
    List<IngredientPrice> findByItemIdOrderByEffectiveDateDesc(UUID itemId);
    List<IngredientPrice> findByItemIdAndSupplierIdOrderByEffectiveDateDesc(UUID itemId, UUID supplierId);

    /**
     * Lấy giá mới nhất của từng item trong danh sách — 1 query duy nhất thay vì N+1.
     * Dùng PostgreSQL DISTINCT ON để lấy row đầu tiên (effective_date DESC) per item_id.
     */
    @Query(value = """
            SELECT DISTINCT ON (ip.item_id) ip.*
            FROM ingredient_price ip
            WHERE ip.item_id IN :itemIds
            ORDER BY ip.item_id, ip.effective_date DESC
            """, nativeQuery = true)
    List<IngredientPrice> findLatestByItemIds(@Param("itemIds") List<UUID> itemIds);
}
