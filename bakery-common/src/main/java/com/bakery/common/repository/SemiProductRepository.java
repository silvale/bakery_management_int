package com.bakery.common.repository;

import com.bakery.common.entity.SemiProduct;
import com.bakery.common.entity.enums.SemiProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SemiProductRepository extends JpaRepository<SemiProduct, UUID> {

    Optional<SemiProduct> findByCode(String code);

    List<SemiProduct> findAllByTypeAndIsActiveTrue(SemiProductType type);

    List<SemiProduct> findAllByIsActiveTrue();

    boolean existsByCode(String code);
}
