package com.bakery.api.batch.repositories;

import com.bakery.api.batch.entities.BatchRun;
import com.bakery.api.framework.enums.BatchRunType;
import com.bakery.api.framework.enums.BatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchRunRepository extends JpaRepository<BatchRun, UUID> {

    /**
     * Kiểm tra process_date này đã có dữ liệu chưa (idempotency).
     * Nếu đã có → set is_rerun = TRUE trên BatchRun mới.
     */
    boolean existsByProcessDateAndRunType(LocalDate processDate, BatchRunType runType);

    List<BatchRun> findAllByProcessDateOrderByStartedAtDesc(LocalDate processDate);

    Optional<BatchRun> findTopByStatusOrderByStartedAtDesc(BatchStatus status);

    @Query("""
        SELECT br FROM BatchRun br
        WHERE br.processDate = :processDate
          AND br.runType = :runType
        ORDER BY br.startedAt DESC
        LIMIT 1
        """)
    Optional<BatchRun> findLatestByProcessDateAndRunType(
        @Param("processDate") LocalDate processDate,
        @Param("runType")     BatchRunType runType
    );
}
