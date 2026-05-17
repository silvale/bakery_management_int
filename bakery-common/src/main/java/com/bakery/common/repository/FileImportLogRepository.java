package com.bakery.common.repository;

import com.bakery.common.entity.FileImportLog;
import com.bakery.common.entity.enums.BatchStatus;
import com.bakery.common.entity.enums.FileType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileImportLogRepository extends JpaRepository<FileImportLog, UUID> {

    List<FileImportLog> findAllByBatchRunId(UUID batchRunId);

    List<FileImportLog> findAllByBatchRunIdAndStatus(UUID batchRunId, BatchStatus status);

    Optional<FileImportLog> findByBatchRunIdAndFileType(UUID batchRunId, FileType fileType);

    /** Kiểm tra file có lỗi không (dùng để quyết định có tiếp tục reconcile không) */
    boolean existsByBatchRunIdAndStatusIn(UUID batchRunId, List<BatchStatus> statuses);
}
