package com.bakery.api.batch.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import com.bakery.api.framework.BaseLogEntity;

/**
 * Track file TXT đã import để tránh re-import.
 * Dùng MD5 checksum của nội dung file.
 */
@Entity
@Table(name = "txt_import_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TxtImportLog extends BaseLogEntity {

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** MD5 checksum. Nếu đã tồn tại → skip */
    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    @Column(name = "process_date", nullable = false)
    private LocalDate processDate;

    @Column(name = "rows_parsed", nullable = false)
    @Builder.Default
    private Integer rowsParsed = 0;

    @Column(name = "rows_ok", nullable = false)
    @Builder.Default
    private Integer rowsOk = 0;

    @Column(name = "rows_error", nullable = false)
    @Builder.Default
    private Integer rowsError = 0;

    @Column(name = "error_detail", columnDefinition = "TEXT")
    private String errorDetail;

    @Column(name = "imported_at", nullable = false)
    @Builder.Default
    private OffsetDateTime importedAt = OffsetDateTime.now();
}
