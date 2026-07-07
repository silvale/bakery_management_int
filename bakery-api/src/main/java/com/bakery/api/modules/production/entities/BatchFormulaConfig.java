package com.bakery.api.modules.production.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Cấu hình công thức sản xuất theo lô/mâm/cối.
 *
 * formula_type = 'LAN_MAM'  → tính số mâm (VD: Bento, tối đa 12/mâm)
 * formula_type = 'LAN_XUAT' → tính số cối bông lan theo ma trận size
 *
 * input_variables:
 *   LAN_MAM   : {"multiplier": 1.0}
 *   LAN_XUAT  : {"multiplier": 1.5, "extra_bap_coi": true}
 *
 * output_yield_mapping:
 *   LAN_MAM   : {"PK-BENTO": 12}
 *   LAN_XUAT  : {"PK-SIZE-12": 3, "PK-SIZE-14": 3, "PK-SIZE-18": 3, "PK-SIZE-20": 2}
 */
@Entity
@Table(name = "batch_formula_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BatchFormulaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "formula_code", nullable = false, unique = true, length = 50)
    private String formulaCode;

    @Column(name = "formula_name", nullable = false, length = 100)
    private String formulaName;

    /** 'LAN_MAM' | 'LAN_XUAT' */
    @Column(name = "formula_type", nullable = false, length = 20)
    private String formulaType;

    /** Prefix sản phẩm đầu vào — dùng để filter demand + daily_inventory */
    @Column(name = "target_product_prefix", nullable = false, length = 20)
    private String targetProductPrefix;

    /** Số lượng tối đa per cối/mâm (dùng cho LAN_MAM ceil) */
    @Column(name = "max_qty_per_batch", nullable = false)
    private int maxQtyPerBatch = 1;

    /** Hệ số và tham số động — {"multiplier": 1.5, "extra_bap_coi": true} */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_variables", columnDefinition = "jsonb")
    private Map<String, Object> inputVariables;

    /** Ma trận đầu ra: product_code → qty per cối/mâm */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output_yield_mapping", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> outputYieldMapping;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy = "system";
}
