package com.bakery.api.master.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.repository.BaseRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository cho Item bất kể type — dùng khi cần lookup theo ID
 * mà không biết trước item_type (e.g., ProductExpiryConfig, InventoryRequestLine).
 */
public interface ItemLookupRepository extends BaseRepository<Item> {
    Optional<Item> findByCode(String code);

    /** 21/BE-3: số mặt hàng thuộc nhóm hàng theo trạng thái (index idx_item_item_group). */
    long countByItemGroupIdAndStatus(UUID itemGroupId, com.bakery.framework.entity.EntityStatus status);

    /**
     * Lấy tất cả Product (item_type = 'PRODUCT') qua JPQL TYPE().
     * Trả List<Item> vì Hibernate proxy không thể cast tự động sang subtype.
     * Caller tự cast sau khi nhận về.
     */
    @Query("SELECT i FROM Item i WHERE TYPE(i) = Product")
    List<Item> findAllProducts();

    /** Trong {@code ids}, các id là thành phẩm (PRODUCT). Truy vấn thay cho instanceof vì item có thể là proxy. */
    @Query("SELECT i.id FROM Item i WHERE i.id IN :ids AND TYPE(i) = Product")
    List<UUID> findProductIdsIn(@Param("ids") Collection<UUID> ids);

    /**
     * 12/BE-1: mặt hàng (không phải thành phẩm, đang dùng) thuộc NCC: NCC mặc định HOẶC có dòng bảng giá ACTIVE của NCC.
     * Trả [id, matchedBy] với matchedBy = DEFAULT | PRICE_LIST | BOTH. Dùng index (supplier_id, item_id) của ingredient_price.
     */
    @Query(value = """
            SELECT i.id AS id,
                   CASE WHEN i.default_supplier_id = :supplierId
                         AND EXISTS (SELECT 1 FROM ingredient_price p
                                      WHERE p.item_id = i.id AND p.supplier_id = :supplierId AND p.status = 'ACTIVE')
                        THEN 'BOTH'
                        WHEN i.default_supplier_id = :supplierId THEN 'DEFAULT'
                        ELSE 'PRICE_LIST' END AS matched_by
              FROM item i
             WHERE i.item_type <> 'PRODUCT' AND i.status = 'ACTIVE'
               AND (i.default_supplier_id = :supplierId
                    OR EXISTS (SELECT 1 FROM ingredient_price p
                                WHERE p.item_id = i.id AND p.supplier_id = :supplierId AND p.status = 'ACTIVE'))
            """, nativeQuery = true)
    List<Object[]> findIdsBySupplier(@Param("supplierId") UUID supplierId);
}
