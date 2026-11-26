package com.bakery.api.production.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.production.entity.ProductionSession;

/** 24: request/response của bảng tạm (`/api/v1/production/*`). */
public final class ProductionPoolDto {
    private ProductionPoolDto() {}

    public record Ref(UUID id, String code, String name) {
        public static Ref of(Warehouse w) { return w == null ? null : new Ref(w.getId(), w.getCode(), w.getName()); }
    }

    // ── Request ─────────────────────────────────────────────────────────────

    public record PickLineRequest(UUID warehouseId, UUID itemId, BigDecimal qty, BigDecimal damagedQty) {}

    public record PickRequest(UUID productionRequestId, String reason, String note, List<PickLineRequest> lines) {}

    public record ConfirmLine(UUID lineId, UUID itemId, BigDecimal qty) {}

    public record ConfirmRequest(List<ConfirmLine> lines) {}

    public record CloseLine(UUID itemId, UUID heldForRequestId, BigDecimal returnQty, BigDecimal writeOffQty,
                            String reasonCode, String note, BigDecimal holdQty, UUID holdForRequestId) {}

    public record CloseRequest(List<CloseLine> lines) {}

    // ── Phiếu bảng tạm (PICK / RETURN / WRITE_OFF) ──────────────────────────

    public record DocLine(UUID id, UUID itemId, String code, String name, String unit, BigDecimal quantity,
                          BigDecimal receivedQty, boolean stockShort, String reasonCode, BigDecimal amount, String note) {}

    /** {@code amount} / {@code totalAmount} null khi user không được xem giá vốn. */
    public record Doc(UUID id, String code, String type, String status, LocalDate requestDate,
                      Ref sourceWarehouse, Ref targetWarehouse, String pickReason, String note,
                      Ref productionRequest, UUID sessionId, String createdBy, Instant createdAt,
                      String receivedBy, LocalDate receivedDate, List<DocLine> lines, BigDecimal totalAmount) {

        /** {@code seeStock}: user xem được tồn kho nguồn (24/§5.8); không thì không lộ tag Thiếu. */
        public static Doc from(InventoryRequest e, boolean seeCost, boolean seeStock) {
            List<DocLine> lines = e.getLines().stream().map(l -> line(l, seeCost, seeStock)).toList();
            BigDecimal total = null;
            if (seeCost) {
                total = e.getLines().stream().map(InventoryRequestLine::getAmount)
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            return new Doc(e.getId(), e.getCode(), e.getRequestType().name(), e.getApprovalStatus().name(), e.getRequestDate(),
                    Ref.of(e.getSourceWarehouse()), Ref.of(e.getTargetWarehouse()), e.getPickReason(), e.getNote(),
                    e.getProductionRequest() == null ? null
                            : new Ref(e.getProductionRequest().getId(), e.getProductionRequest().getCode(), null),
                    e.getSessionId(), e.getCreatedBy(), e.getCreatedAt(), e.getReceivedBy(), e.getReceivedDate(), lines, total);
        }

        private static DocLine line(InventoryRequestLine l, boolean seeCost, boolean seeStock) {
            return new DocLine(l.getId(), l.getItem().getId(), l.getItem().getCode(), l.getItem().getName(), l.getUnit(),
                    l.getQuantity(), l.getReceivedQty(), seeStock && l.isStockShort(), l.getReasonCode(),
                    seeCost ? l.getAmount() : null, l.getNote());
        }
    }

    public record Created(List<CreatedPick> created) {}

    public record CreatedPick(UUID id, String code, Ref sourceWarehouse) {}

    // ── Phiên ───────────────────────────────────────────────────────────────

    /** {@code receiptCount} chỉ có ở danh sách phiên; null ở nơi khác (vd. {@code GET /pool}). */
    public record SessionInfo(UUID id, LocalDate date, String status, String openedBy, Instant openedAt,
                              String closedBy, Instant closedAt, Integer receiptCount) {
        public static SessionInfo from(ProductionSession s) { return from(s, null); }

        public static SessionInfo from(ProductionSession s, Integer receiptCount) {
            return s == null ? null : new SessionInfo(s.getId(), s.getSessionDate(), s.getSessionStatus().name(),
                    s.getOpenedBy(), s.getOpenedAt(), s.getClosedBy(), s.getClosedAt(), receiptCount);
        }
    }

    public record PrRef(UUID id, String code) {}

    public record ReceiptLine(UUID itemId, String name, String unit, BigDecimal quantity, BigDecimal receivedQty,
                              String reasonCode, String note, BigDecimal amount) {}

    /** Một phiếu của phiên. {@code receivedAt} = lúc thi hành; {@code amount} chỉ WRITE_OFF và chỉ khi xem được giá vốn. */
    public record Receipt(UUID id, String code, String requestType, String pickReason, PrRef productionRequest,
                          Ref sourceWarehouse, Ref targetWarehouse, String createdBy, Instant createdAt,
                          String receivedBy, Instant receivedAt, BigDecimal amount, List<ReceiptLine> lines) {

        public static Receipt from(InventoryRequest e, boolean seeCost) {
            boolean writeOff = e.getRequestType() == com.bakery.framework.entity.InventoryRequestType.WRITE_OFF;
            List<ReceiptLine> lines = e.getLines().stream().map(l -> new ReceiptLine(l.getItem().getId(), l.getItem().getName(),
                    l.getUnit(), l.getQuantity(), l.getReceivedQty(), l.getReasonCode(), l.getNote(),
                    writeOff && seeCost ? l.getAmount() : null)).toList();
            BigDecimal amount = writeOff && seeCost ? e.getLines().stream().map(InventoryRequestLine::getAmount)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            return new Receipt(e.getId(), e.getCode(), e.getRequestType().name(), e.getPickReason(),
                    e.getProductionRequest() == null ? null : new PrRef(e.getProductionRequest().getId(), e.getProductionRequest().getCode()),
                    Ref.of(e.getSourceWarehouse()), Ref.of(e.getTargetWarehouse()), e.getCreatedBy(), e.getCreatedAt(),
                    e.getReceivedBy(), e.getApprovedAt(), amount, lines);
        }

        /** Thời gian thi hành, không có thì lúc tạo (để xếp thứ tự). */
        public Instant sortKey() { return receivedAt != null ? receivedAt : createdAt; }
    }

    /** {@code GET /sessions/{id}}: các field của phiên + {@code receipts}. */
    public record SessionDetail(UUID id, LocalDate date, String status, String openedBy, Instant openedAt,
                                String closedBy, Instant closedAt, Integer receiptCount, List<Receipt> receipts) {
        public static SessionDetail of(SessionInfo s, List<Receipt> receipts) {
            return new SessionDetail(s.id(), s.date(), s.status(), s.openedBy(), s.openedAt(), s.closedBy(), s.closedAt(),
                    receipts.size(), receipts);
        }
    }

    // ── Bảng tạm ────────────────────────────────────────────────────────────

    public record Held(UUID requestId, String code, BigDecimal qty) {}

    public record PoolItem(UUID itemId, String name, String unit, Ref homeWarehouse,
                           BigDecimal picked, BigDecimal consumed, BigDecimal writtenOff, BigDecimal shared, List<Held> held) {}

    public record Pool(Ref warehouse, SessionInfo session, SessionInfo staleSession, List<PoolItem> items) {}

    /** 24/BE-5: tồn của một kho nguồn; chỉ xuất hiện ở kho user xem được tồn. */
    public record StockAt(UUID warehouseId, BigDecimal qty) {}

    /** 24/BE-5: một kết quả tìm NL / BTP ở màn chọn hàng. {@code stock} chỉ có kho mà user xem được tồn. */
    public record Pickable(UUID itemId, String name, String itemType, String unit, Ref homeWarehouse, List<StockAt> stock) {}

    /** 24/BE-5: kho "Sản xuất lấy NL" cho ô "Lấy ở". */
    public record Source(UUID id, String code, String name, boolean canSeeStock) {}

    // ── Danh sách NL của phiếu SX ───────────────────────────────────────────

    public record MaterialRow(UUID itemId, String name, String unit, BigDecimal needed, BigDecimal held, BigDecimal consumed) {}

    public record Materials(List<MaterialRow> items) {}

    /** Chi phí NL của phiếu SX (chỉ admin). */
    public record MaterialCost(BigDecimal used, BigDecimal damaged, BigDecimal total) {}
}
