package com.bakery.api.modules.masterdata.repositories;

import com.bakery.api.modules.masterdata.entities.SemiProduct;
import com.bakery.api.framework.enums.SemiProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SemiProductRepository extends JpaRepository<SemiProduct, UUID>, JpaSpecificationExecutor<SemiProduct> {

    Optional<SemiProduct> findByCode(String code);

    List<SemiProduct> findAllByTypeAndIsActiveTrue(SemiProductType type);

    List<SemiProduct> findAllByIsActiveTrue();

    boolean existsByCode(String code);
}
