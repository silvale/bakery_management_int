package com.bakery.common.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Composite PK cho view v_reconciliation.
 * (item_id, recon_date, branch_id) — COALESCE đảm bảo 3 field này luôn có giá trị.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReconciliationViewId implements Serializable {
    private UUID      itemId;
    private LocalDate reconDate;
    private UUID      branchId;
}
