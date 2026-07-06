package com.bakery.common.repository;

import com.bakery.common.entity.BatchFormulaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchFormulaConfigRepository extends JpaRepository<BatchFormulaConfig, UUID> {

    List<BatchFormulaConfig> findAllByActiveTrue();

    List<BatchFormulaConfig> findAllByFormulaTypeAndActiveTrue(String formulaType);

    Optional<BatchFormulaConfig> findByFormulaCode(String formulaCode);
}
