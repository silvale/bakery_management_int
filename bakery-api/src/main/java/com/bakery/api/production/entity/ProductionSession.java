package com.bakery.api.production.entity;

import java.time.Instant;
import java.time.LocalDate;

import com.bakery.api.master.entity.Store;
import com.bakery.framework.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 24/§4.4: phiên ngày của bảng tạm, mỗi cửa hàng một phiên mỗi ngày. Là ranh giới đối soát, không phải giờ 0h. */
@Getter
@Setter
@Entity
@Table(name = "production_session")
public class ProductionSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 10)
    private SessionStatus sessionStatus = SessionStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_by", length = 100)
    private String openedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by", length = 100)
    private String closedBy;
}
