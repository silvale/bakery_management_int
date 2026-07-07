package com.bakery.api.batch.entities;

import com.bakery.api.framework.enums.BatchRunType;
import com.bakery.api.framework.enums.BatchStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import com.bakery.api.framework.BaseLogEntity;

/**
 * Mỗi lần chạy Spring Batch.
 * Extend BaseSystemEntity (không có approved_by/at — system tự ghi).
 *
 * Idempotency strategy: OVERWRITE
 *   Chạy lại cùng ngày → ghi đè dữ liệu cũ, is_rerun = TRUE.
 */
@Entity
@Table(name = "batch_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchRun extends BaseLogEntity {

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "run_type", nullable = false, length = 30)
    private BatchRunType runType;

    @Column(name = "process_date", nullable = false)
    private LocalDate processDate;

    /** TRUE nếu process_date này đã có dữ liệu → đây là lần chạy lại */
    @Column(name = "is_rerun", nullable = false)
    @Builder.Default
    private Boolean isRerun = false;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BatchStatus status = BatchStatus.RUNNING;

    /** Username hoặc 'SCHEDULER' */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    // -------------------------------------------------------
    // Relationships
    // -------------------------------------------------------
    @OneToMany(mappedBy = "batchRun", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FileImportLog> fileImportLogs = new ArrayList<>();
}
