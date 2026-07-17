package com.bakery.api.master.repository;

import java.util.List;
import java.util.Optional;

import com.bakery.api.master.entity.UnitConversion;
import com.bakery.api.master.entity.UnitConversionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UnitConversionRepository
        extends JpaRepository<UnitConversion, UnitConversionId> {

    /**
     * Tra cứu conversion — case-insensitive, normalize về uppercase.
     * Ví dụ: fromUnit="g", toUnit="kg" → tìm (G, KG).
     */
    @Query("SELECT u FROM UnitConversion u " +
           "WHERE UPPER(u.fromUnit) = UPPER(:fromUnit) " +
           "AND   UPPER(u.toUnit)   = UPPER(:toUnit)")
    Optional<UnitConversion> findConversion(
            @Param("fromUnit") String fromUnit,
            @Param("toUnit")   String toUnit);

    /** Lấy tất cả conversion có from_unit = ? (dùng gợi ý UI). */
    @Query("SELECT u FROM UnitConversion u WHERE UPPER(u.fromUnit) = UPPER(:fromUnit)")
    List<UnitConversion> findByFromUnit(@Param("fromUnit") String fromUnit);
}
