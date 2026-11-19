package com.bakery.api.cake.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CakeDesignRepository extends BaseRepository<CakeDesign> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    /** Mẫu đang dùng của các thành phẩm (dùng để biết thành phẩm nào là "bánh kem"). */
    List<CakeDesign> findByProductIdAndActiveTrue(UUID productId);

    /** Id các thành phẩm có ít nhất 1 mẫu (kể cả đã ngừng dùng). */
    @Query("SELECT DISTINCT d.product.id FROM CakeDesign d WHERE d.product.id IN :productIds")
    List<UUID> findProductIdsHavingDesign(@Param("productIds") Collection<UUID> productIds);

    /** Id mọi thành phẩm có ít nhất 1 mẫu (kể cả đã ngừng dùng). */
    @Query("SELECT DISTINCT d.product.id FROM CakeDesign d")
    List<UUID> findAllDesignedProductIds();

    @Query("SELECT d FROM CakeDesign d WHERE d.product.id IN :productIds")
    List<CakeDesign> findByProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query("SELECT d FROM CakeDesign d WHERE d.product.id = :productId")
    List<CakeDesign> findAllByProduct(@Param("productId") UUID productId);
}
