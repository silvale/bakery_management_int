package com.bakery.api.batch.repositories;

import com.bakery.api.batch.entities.FileImportLog;
import com.bakery.api.framework.enums.BatchStatus;
import com.bakery.api.framework.enums.FileType;
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
