package com.bakery.api.cake.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.bakery.api.cake.entity.DailyReportLineDesign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailyReportLineDesignRepository extends JpaRepository<DailyReportLineDesign, UUID> {

    boolean existsByCakeDesignId(UUID cakeDesignId);

    /** Mọi dòng mẫu của một báo cáo, kèm mẫu và dòng thành phẩm. */
    @Query("""
            SELECT x FROM DailyReportLineDesign x JOIN FETCH x.cakeDesign JOIN FETCH x.line l
            WHERE l.dailyReport.id = :reportId""")
    List<DailyReportLineDesign> findByReportId(@Param("reportId") UUID reportId);

    /** Mẫu của các báo cáo FINALIZED trong khoảng ngày (theo index report_date), kèm mẫu, thành phẩm và ngày. */
    @Query("""
            SELECT x FROM DailyReportLineDesign x
              JOIN FETCH x.cakeDesign d JOIN FETCH d.product
              JOIN FETCH x.line l JOIN FETCH l.dailyReport r
            WHERE r.status = :status AND r.reportDate BETWEEN :from AND :to""")
    List<DailyReportLineDesign> findFinalizedBetween(@Param("status") com.bakery.framework.entity.DailyReportStatus status,
            @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    @Query("SELECT x FROM DailyReportLineDesign x JOIN FETCH x.cakeDesign WHERE x.line.id IN :lineIds")
    List<DailyReportLineDesign> findByLineIds(@Param("lineIds") Collection<UUID> lineIds);

    @Query("SELECT x FROM DailyReportLineDesign x WHERE x.line.id = :lineId AND x.cakeDesign.id = :designId")
    java.util.Optional<DailyReportLineDesign> findByLineAndDesign(
            @Param("lineId") UUID lineId, @Param("designId") UUID designId);
}
