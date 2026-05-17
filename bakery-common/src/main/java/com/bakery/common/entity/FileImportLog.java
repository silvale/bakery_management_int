package com.bakery.common.entity;

import com.bakery.common.entity.enums.BatchStatus;
import com.bakery.common.entity.enums.FileType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Log chi tiết từng file Excel xử lý trong batch.
 *
 * error_row_indices (JSONB):
 *   Lưu danh sách dòng lỗi để nhân viên biết chính xác dòng nào
 *   cần sửa trong file Excel.
 *
 *   Format: [{"row": 5, "col": "qty_actual", "msg": "Không phải số"},
 *             {"row": 12, "col": "product_code", "msg": "Không tìm thấy"}]
 */
@Entity
@Table(name = "file_import_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileImportLog extends BaseSystemEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_run_id", nullable = false)
    private BatchRun batchRun;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 50)
    private FileType fileType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BatchStatus status = BatchStatus.RUNNING;

    @Column(name = "rows_total")
    private Integer rowsTotal;

    @Column(name = "rows_ok")
    private Integer rowsOk;

    @Column(name = "rows_error")
    private Integer rowsError;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    /**
     * JSONB: danh sách dòng lỗi.
     * List<Map<String, String>>:
     *   [{"row": "5", "col": "qty_actual", "msg": "Không phải số"}]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_row_indices", columnDefinition = "jsonb")
    private List<Map<String, String>> errorRowIndices;

    @Column(name = "imported_at", nullable = false)
    @Builder.Default
    private OffsetDateTime importedAt = OffsetDateTime.now();
}
