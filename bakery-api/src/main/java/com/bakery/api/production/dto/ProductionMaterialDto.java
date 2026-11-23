package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.bakery.framework.metadata.ReferenceValue;

/** 20: danh sách lấy NL của phiếu SX bán thành phẩm + payload các thao tác lấy/trả/hao hụt. */
public final class ProductionMaterialDto {
    private ProductionMaterialDto() {}

    /** GET /production-requests/{id}/materials */
    public record Materials(boolean closed, String closedBy, Instant closedAt, List<Row> items) {}

    /** Một NL: cần, đã lấy, đang ở kho nào, ai lấy. {@code toPick} = max(0, needed − picked + returned + writtenOff). */
    public record Row(UUID itemId, ReferenceValue item, String unit,
                      BigDecimal needed, BigDecimal picked, BigDecimal consumed, BigDecimal returned,
                      BigDecimal writtenOff, BigDecimal remaining, BigDecimal toPick,
                      List<StockAt> stock, List<Pick> picks) {}

    public record StockAt(UUID warehouseId, ReferenceValue warehouse, BigDecimal qty) {}

    /** Một lượt lấy ở một kho (gộp các lô của cùng lượt). */
    public record Pick(UUID warehouseId, ReferenceValue warehouse, BigDecimal qty, String pickedBy, Instant pickedAt) {}

    public record Line(UUID itemId, BigDecimal qty) {}

    public record PickRequest(UUID warehouseId, List<Line> lines) {}

    public record ReturnRequest(List<Line> lines) {}

    public record WriteOffLine(UUID itemId, BigDecimal qty, String reasonCode, String note) {}

    public record WriteOffRequest(List<WriteOffLine> lines) {}
}
