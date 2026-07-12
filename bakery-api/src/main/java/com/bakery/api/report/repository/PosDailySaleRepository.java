package com.bakery.api.report.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.report.entity.PosDailySale;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosDailySaleRepository extends JpaRepository<PosDailySale, UUID> {
    List<PosDailySale> findBySaleDate(LocalDate saleDate);
    void deleteBySaleDate(LocalDate saleDate);
}
