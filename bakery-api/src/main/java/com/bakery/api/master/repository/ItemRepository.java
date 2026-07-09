package com.bakery.api.master.repository;

import com.bakery.api.master.entity.Item;
import com.bakery.framework.repository.BaseRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Base repository cho toàn bộ Item (Ingredient, SemiProduct, Product).
 * Spring Data JPA tự động thêm discriminator filter khi dùng subtype repository.
 */
@NoRepositoryBean
public interface ItemRepository<E extends Item> extends BaseRepository<E> {
    java.util.Optional<E> findByCode(String code);
    boolean existsByCode(String code);
}
