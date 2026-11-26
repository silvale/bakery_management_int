package com.bakery.api.production.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.production.entity.ProductionSession;
import com.bakery.framework.repository.BaseRepository;

public interface ProductionSessionRepository extends BaseRepository<ProductionSession> {

    Optional<ProductionSession> findByStoreIdAndSessionDate(UUID storeId, LocalDate date);

    /** Phiên của cửa hàng từ ngày {@code from} (gồm) trở đi, ngày mới trước; dùng kiểm "còn phiên cũ mở" và "đã có phiên sau". */
    List<ProductionSession> findByStoreIdAndSessionDateGreaterThanEqualOrderBySessionDateDesc(UUID storeId, LocalDate from);

    List<ProductionSession> findByStoreIdAndSessionDateBetweenOrderBySessionDateDesc(UUID storeId, LocalDate from, LocalDate to);

    /** Phiên đang mở (tối đa vài phiên), ngày cũ trước. */
    List<ProductionSession> findByStoreIdAndSessionStatusOrderBySessionDateAsc(UUID storeId,
            com.bakery.api.production.entity.SessionStatus status);
}
