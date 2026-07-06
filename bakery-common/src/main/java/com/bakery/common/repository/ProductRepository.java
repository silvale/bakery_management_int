package com.bakery.common.repository;

import com.bakery.common.entity.Product;
import com.bakery.common.entity.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /** Lookup theo mã SP từ file Excel (VD: SP022575) */
    Optional<Product> findByCode(String code);

    List<Product> findAllByIsActiveTrue();

    List<Product> findAllByProductTypeAndIsActiveTrue(ProductType productType);

    boolean existsByCode(String code);

    /**
     * Batch lookup: tìm nhiều sản phẩm theo danh sách mã cùng lúc.
     * Dùng trong Batch reader để tránh N+1.
     */
    @Query("SELECT p FROM Product p WHERE p.code IN :codes AND p.isActive = true")
    List<Product> findAllByCodeIn(@Param("codes") Set<String> codes);
}
