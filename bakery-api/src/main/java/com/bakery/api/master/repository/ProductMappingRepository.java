package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.master.entity.ProductMapping;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMappingRepository extends BaseRepository<ProductMapping> {

    List<ProductMapping> findByItemId(UUID itemId);

    Optional<ProductMapping> findByExCode(String exCode);

    boolean existsByExCode(String exCode);

    /** Trả về item_id trực tiếp — không load entity Item, tránh LazyInitializationException */
    @Query("SELECT pm.item.id FROM ProductMapping pm WHERE pm.exCode = :exCode")
    Optional<UUID> findItemIdByExCode(@Param("exCode") String exCode);
}
