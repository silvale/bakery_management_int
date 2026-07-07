package com.bakery.api.modules.production.repositories;

import com.bakery.api.modules.production.entities.BatchFormulaConfig;
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
