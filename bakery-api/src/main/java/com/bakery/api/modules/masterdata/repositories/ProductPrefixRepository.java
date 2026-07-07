package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.ProductPrefix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductPrefixRepository extends JpaRepository<ProductPrefix, UUID> {

    Optional<ProductPrefix> findByPrefixAndIsActiveTrue(String prefix);

    List<ProductPrefix> findAllByIsActiveTrue();

    boolean existsByPrefix(String prefix);

    /**
     * Lấy tất cả prefix đang active, sắp xếp theo độ dài GIẢM DẦN.
     * Dùng cho longest-match khi decode EX_CODE.
     * VD: "BKST" phải được thử trước "BK" để tránh match sai.
     */
    @Query("""
        SELECT pp FROM ProductPrefix pp
        JOIN FETCH pp.product
        WHERE pp.isActive = TRUE
        ORDER BY LENGTH(pp.prefix) DESC
        """)
    List<ProductPrefix> findAllActiveOrderByPrefixLengthDesc();
}
