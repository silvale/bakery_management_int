package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.InventoryRequestLine;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.entity.StockMovement;
import com.bakery.api.inventory.repository.InventoryRequestRepository;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.repository.StockMovementRepository;
import com.bakery.api.inventory.service.StockFifo;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.entity.MovementType;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 24: tạo phiếu bảng tạm (PICK / RETURN / WRITE_OFF) và ghi hao hụt. Dùng chung cho lấy thêm và đóng phiên. */
@Component
@RequiredArgsConstructor
public class WipDocs {

    static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final String REF_TYPE = "INVENTORY_REQUEST";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InventoryRequestRepository requestRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;
    private final BakeryActorResolver actorResolver;

    public static LocalDate today() { return LocalDate.now(VN); }

    /**
     * Phiếu mới chưa lưu dòng. {@code PICK} chờ lấy (PENDING_APPROVAL); {@code RETURN}/{@code WRITE_OFF} thi hành ngay
     * (APPROVED, người gửi là người đếm).
     */
    public InventoryRequest newDoc(InventoryRequestType type, LocalDate date, Warehouse source, Warehouse target,
                                   ProductionRequest pr, String reason, String note, UUID sessionId) {
        InventoryRequest e = new InventoryRequest();
        e.setRequestType(type);
        e.setRequestDate(date);
        e.setSourceWarehouse(source);
        e.setTargetWarehouse(target);
        e.setProductionRequest(pr);
        e.setPickReason(reason);
        e.setNote(note);
        e.setSessionId(sessionId);
        e.setCode(nextCode(type));
        if (type == InventoryRequestType.PICK) {
            e.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        } else {
            e.setApprovalStatus(ApprovalStatus.APPROVED);
            e.setApprovedAt(java.time.Instant.now());
            e.setApprovedBy(actorResolver.currentUserId());
            e.setReceivedBy(actorResolver.currentUsername());
            e.setReceivedDate(today());
        }
        return e;
    }

    public InventoryRequestLine addLine(InventoryRequest e, Item item, BigDecimal qty) {
        InventoryRequestLine l = new InventoryRequestLine();
        l.setInventoryRequest(e);
        l.setItem(item);
        l.setQuantity(qty);
        l.setUnit(item.getUnit());
        l.setSortOrder(e.getLines().size());
        e.getLines().add(l);
        return l;
    }

    private String nextCode(InventoryRequestType type) {
        String prefix = switch (type) {
            case PICK -> "PK";
            case RETURN -> "RT";
            case WRITE_OFF -> "WO";
            default -> throw new IllegalArgumentException("Không phải phiếu bảng tạm: " + type);
        };
        String p = prefix + "-" + LocalDate.now().format(DATE_FMT) + "-";
        return p + String.format("%03d", requestRepository.countByCodeStartingWith(p) + 1);
    }

    /**
     * Hao hụt {@code qty} của {@code item} ở bảng tạm: trừ lô, ghi OUT gắn phiếu {@code doc}, ghi dòng + thành tiền.
     * {@code prId} != null = lô giữ cho phiếu đó trước rồi lô chung (lấy thêm vì hư); {@code group} = chỉ đúng nhóm giữ
     * (đóng phiên; null = lô chung). Thiếu → IllegalStateException.
     */
    public BigDecimal writeOffLine(InventoryRequest doc, Warehouse wip, Item item, BigDecimal qty,
                                   UUID prIdFirst, boolean groupOnly, UUID group, String reasonCode, String note) {
        List<StockLot> lots = stockLotRepository
                .findByItemIdAndWarehouseIdAndQtyRemainingGreaterThanOrderByReceivedDateAscCreatedAtAsc(
                        item.getId(), wip.getId(), BigDecimal.ZERO);
        BigDecimal[] amount = {BigDecimal.ZERO};
        java.util.function.BiConsumer<StockLot, BigDecimal> out = (lot, taken) -> {
            StockMovement mv = new StockMovement();
            mv.setLot(lot);
            mv.setMovementType(MovementType.OUT);
            mv.setQty(taken);
            mv.setRefType(REF_TYPE);
            mv.setRefId(doc.getId());
            mv.setNote("Hao hụt bảng tạm, phiếu " + doc.getCode());
            stockMovementRepository.save(mv);
            stockLotRepository.save(lot);
            amount[0] = amount[0].add(taken.multiply(lot.getUnitCost()));
        };
        BigDecimal left = groupOnly
                ? StockFifo.deduct(lots.stream().filter(l -> java.util.Objects.equals(l.getReservedForRequestId(), group)).toList(), qty, out)
                : WipStock.deduct(lots, prIdFirst, qty, out);
        if (left.signum() > 0) {
            throw new IllegalStateException("Bảng tạm không đủ " + item.getName() + ": cần " + plain(qty)
                    + ", còn " + plain(qty.subtract(left)));
        }
        InventoryRequestLine l = addLine(doc, item, qty);
        l.setReasonCode(reasonCode);
        l.setNote(note);
        BigDecimal rounded = amount[0].setScale(2, RoundingMode.HALF_UP);
        l.setAmount(rounded);
        return rounded;
    }

    static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
}
