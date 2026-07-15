package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Repository cho Item bất kể type — dùng khi cần lookup theo ID
 * mà không biết trước item_type (e.g., ProductExpiryConfig, InventoryRequestLine).
 */
public interface ItemLookupRepository extends BaseRepository<Item> {
    Optional<Item> findByCode(String code);

    /**
     * Lấy tất cả Product (item_type = 'PRODUCT') qua JPQL TYPE().
     * Trả List<Item> vì Hibernate proxy không thể cast tự động sang subtype.
     * Caller tự cast sau khi nhận về.
     */
    @Query("SELECT i FROM Item i WHERE TYPE(i) = Product")
    List<Item> findAllProducts();
}
