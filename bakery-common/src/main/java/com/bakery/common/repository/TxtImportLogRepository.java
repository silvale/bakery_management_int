package com.bakery.common.repository;

import com.bakery.common.entity.TxtImportLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TxtImportLogRepository extends JpaRepository<TxtImportLog, UUID> {

    /** Kiểm tra file đã import chưa bằng MD5 hash */
    boolean existsByFileHash(String fileHash);

    Optional<TxtImportLog> findByFileHash(String fileHash);
}
