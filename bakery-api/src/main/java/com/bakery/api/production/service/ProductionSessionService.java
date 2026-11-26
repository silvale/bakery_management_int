package com.bakery.api.production.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.auth.service.AccessScope;
import com.bakery.api.inventory.entity.InventoryRequest;
import com.bakery.api.inventory.entity.StockLot;
import com.bakery.api.inventory.repository.InventoryRequestRepository;
import com.bakery.api.inventory.repository.StockLotRepository;
import com.bakery.api.inventory.service.StockTransfer;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.Store;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.service.WarehouseRoles;
import com.bakery.api.production.dto.ProductionPoolDto;
import com.bakery.api.production.dto.ProductionPoolDto.CloseLine;
import com.bakery.api.production.dto.ProductionPoolDto.CloseRequest;
import com.bakery.api.production.dto.ProductionPoolDto.Receipt;
import com.bakery.api.production.dto.ProductionPoolDto.SessionDetail;
import com.bakery.api.production.dto.ProductionPoolDto.SessionInfo;
import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionSession;
import com.bakery.api.production.entity.SessionStatus;
import com.bakery.api.production.repository.ProductionRequestRepository;
import com.bakery.api.production.repository.ProductionSessionRepository;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.entity.InventoryRequestType;
import com.bakery.framework.entity.ProductionLineStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.security.BakeryActorResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 24/§4.4, §5.6: phiên ngày của bảng tạm: mở tự động khi xác nhận lấy, đóng/mở lại bởi bếp trưởng, lịch sử. */
@Service
@RequiredArgsConstructor
public class ProductionSessionService {

    private static final DateTimeFormatter DM = DateTimeFormatter.ofPattern("dd/MM");

    private final ProductionSessionRepository sessionRepository;
    private final InventoryRequestRepository requestRepository;
    private final ProductionRequestRepository productionRequestRepository;
    private final StockLotRepository stockLotRepository;
    private final StockTransfer stockTransfer;
    private final WipDocs docs;
    private final WarehouseRoles roles;
    private final BakeryActorResolver actorResolver;
    private final AccessScope scope;

    // ── Quy tắc phiên (hàm thuần) ───────────────────────────────────────────

    /**
     * Lý do không xác nhận lấy được cho ngày {@code d}, null nếu được. {@code sessions} = các phiên đang mở của cửa hàng
     * cộng phiên của ngày {@code d} nếu có.
     */
    static String sessionBlocker(List<ProductionSession> sessions, LocalDate d) {
        for (ProductionSession s : sessions) {
            if (s.getSessionStatus() == SessionStatus.OPEN && s.getSessionDate().isBefore(d)) {
                return "Phiên ngày " + s.getSessionDate().format(DM) + " chưa đóng, bếp trưởng đóng phiên trước";
            }
        }
        for (ProductionSession s : sessions) {
            if (s.getSessionDate().equals(d) && s.getSessionStatus() == SessionStatus.CLOSED) {
                return "Phiên ngày " + d.format(DM) + " đã đóng";
            }
        }
        return null;
    }

    /** Phiên của ngày {@code d} trong danh sách, hoặc null (xác nhận lấy đầu tiên của ngày sẽ mở phiên mới). */
    static ProductionSession sessionOn(List<ProductionSession> sessions, LocalDate d) {
        return sessions.stream().filter(s -> s.getSessionDate().equals(d)).findFirst().orElse(null);
    }

    /** Tổng ba số của một dòng đóng phiên phải bằng phần còn của nhóm. Trả lý do (400) hoặc null. */
    static String closeLineBlocker(String itemName, BigDecimal remaining, BigDecimal ret, BigDecimal wo, BigDecimal hold) {
        if (ret.signum() < 0 || wo.signum() < 0 || hold.signum() < 0) return itemName + ": số lượng không được âm";
        BigDecimal sum = ret.add(wo).add(hold);
        if (sum.compareTo(remaining) != 0) {
            return itemName + ": trả + hao hụt + giữ (" + WipDocs.plain(sum) + ") phải bằng phần còn (" + WipDocs.plain(remaining) + ")";
        }
        return null;
    }

    // ── Mở phiên ────────────────────────────────────────────────────────────

    /** Kiểm quy tắc rồi trả phiên của ngày {@code d}; chưa có thì mở. Gọi ở xác nhận lấy. */
    @Transactional
    public ProductionSession openFor(Store store, LocalDate d) {
        List<ProductionSession> view = new ArrayList<>(sessionRepository
                .findByStoreIdAndSessionStatusOrderBySessionDateAsc(store.getId(), SessionStatus.OPEN));
        sessionRepository.findByStoreIdAndSessionDate(store.getId(), d)
                .filter(s -> !view.contains(s)).ifPresent(view::add);
        String blocked = sessionBlocker(view, d);
        if (blocked != null) throw new IllegalStateException(blocked);
        ProductionSession s = sessionOn(view, d);
        if (s != null) return s;
        s = new ProductionSession();
        s.setStore(store);
        s.setSessionDate(d);
        s.setSessionStatus(SessionStatus.OPEN);
        s.setOpenedAt(Instant.now());
        s.setOpenedBy(actorResolver.currentUsername());
        return sessionRepository.save(s);
    }

    /** Phiên đang mở mới nhất của cửa hàng, hoặc null. */
    public ProductionSession currentOpen(Store store) {
        List<ProductionSession> open = sessionRepository.findByStoreIdAndSessionStatusOrderBySessionDateAsc(store.getId(), SessionStatus.OPEN);
        return open.isEmpty() ? null : open.get(open.size() - 1);
    }

    // ── Đóng / mở lại ───────────────────────────────────────────────────────

    private record Group(UUID itemId, UUID reservedFor) {}

    @Transactional
    public SessionDetail close(UUID id, CloseRequest req) {
        ProductionSession s = load(id);
        if (s.getSessionStatus() != SessionStatus.OPEN) throw new IllegalStateException("Phiên ngày " + s.getSessionDate().format(DM) + " đã đóng");
        Warehouse wip = roles.workInProgress();
        if (wip.getStore() == null || !wip.getStore().getId().equals(s.getStore().getId())) {
            throw new IllegalStateException("Phiên không thuộc cửa hàng của kho bảng tạm");
        }
        List<CloseLine> lines = req == null || req.lines() == null ? List.of() : req.lines();

        // Phần còn theo nhóm (mặt hàng, giữ cho phiếu nào)
        List<StockLot> lots = stockLotRepository.findByWarehouseIdAndQtyRemainingGreaterThan(wip.getId(), BigDecimal.ZERO);
        Map<Group, BigDecimal> remaining = new LinkedHashMap<>();
        Map<UUID, Item> items = new LinkedHashMap<>();
        for (StockLot l : lots) {
            remaining.merge(new Group(l.getItem().getId(), l.getReservedForRequestId()), l.getQtyRemaining(), BigDecimal::add);
            items.putIfAbsent(l.getItem().getId(), l.getItem());
        }

        // Kiểm toàn bộ dòng TRƯỚC khi động tồn
        Set<Group> seen = new java.util.HashSet<>();
        for (CloseLine cl : lines) {
            Group g = new Group(cl.itemId(), cl.heldForRequestId());
            if (!seen.add(g)) throw new IllegalArgumentException("Một nhóm xuất hiện hai lần trong danh sách đóng phiên");
            BigDecimal rem = remaining.getOrDefault(g, BigDecimal.ZERO);
            Item item = items.get(cl.itemId());
            String name = item != null ? item.getName() : String.valueOf(cl.itemId());
            String msg = closeLineBlocker(name, rem, nz(cl.returnQty()), nz(cl.writeOffQty()), nz(cl.holdQty()));
            if (msg != null) throw new IllegalArgumentException(msg);
            if (nz(cl.writeOffQty()).signum() > 0) {
                if (cl.reasonCode() == null || cl.reasonCode().isBlank()) throw new IllegalArgumentException(name + ": hao hụt phải chọn lý do");
                if ("OTHER".equals(cl.reasonCode()) && (cl.note() == null || cl.note().isBlank())) {
                    throw new IllegalArgumentException(name + ": lý do Khác phải ghi chú");
                }
            }
            if (nz(cl.holdQty()).signum() > 0) {
                if (cl.holdForRequestId() == null) throw new IllegalArgumentException(name + ": giữ cho phiếu nào?");
                requireHoldable(cl.holdForRequestId(), name);
            }
        }

        // Áp dụng: hao hụt, trả, giữ
        InventoryRequest writeOff = null;
        Map<UUID, InventoryRequest> returns = new LinkedHashMap<>();
        List<InventoryRequest> created = new ArrayList<>();
        for (CloseLine cl : lines) {
            Item item = items.get(cl.itemId());
            if (item == null) continue;   // nhóm trống (phần còn 0 và cả ba số 0)
            UUID group = cl.heldForRequestId();
            BigDecimal wo = nz(cl.writeOffQty());
            BigDecimal ret = nz(cl.returnQty());
            BigDecimal hold = nz(cl.holdQty());
            if (wo.signum() > 0) {
                if (writeOff == null) {
                    writeOff = requestRepository.save(docs.newDoc(InventoryRequestType.WRITE_OFF, s.getSessionDate(), wip, null,
                            null, null, null, s.getId()));
                    created.add(writeOff);
                }
                docs.writeOffLine(writeOff, wip, item, wo, null, true, group, cl.reasonCode(), cl.note());
            }
            if (ret.signum() > 0) {
                Warehouse dest = item.getDefaultWarehouse();
                if (dest == null) throw new IllegalStateException(item.getName() + " chưa có kho lưu trữ");
                InventoryRequest rt = returns.get(dest.getId());
                if (rt == null) {
                    rt = requestRepository.save(docs.newDoc(InventoryRequestType.RETURN, s.getSessionDate(), wip, dest,
                            null, null, null, s.getId()));
                    returns.put(dest.getId(), rt);
                    created.add(rt);
                }
                BigDecimal left = stockTransfer.move(item, ret, wip, dest, group, null, s.getSessionDate(), WipDocs.REF_TYPE, rt.getId(),
                        "Trả NL về kho lưu trữ, phiếu " + rt.getCode(), "Nhận NL trả từ bảng tạm, phiếu " + rt.getCode());
                if (left.signum() > 0) throw new IllegalStateException(item.getName() + ": bảng tạm chỉ còn " + WipDocs.plain(ret.subtract(left)));
                docs.addLine(rt, item, ret).setReceivedQty(ret);
            }
            if (hold.signum() > 0 && !Objects.equals(cl.holdForRequestId(), group)) {
                BigDecimal left = stockTransfer.move(item, hold, wip, wip, group, cl.holdForRequestId(), s.getSessionDate(),
                        WipDocs.REF_TYPE, s.getId(), "Chuyển giữ cho phiếu SX", "Giữ cho phiếu SX");
                if (left.signum() > 0) throw new IllegalStateException(item.getName() + ": bảng tạm chỉ còn " + WipDocs.plain(hold.subtract(left)));
            }
        }
        requestRepository.saveAll(created);

        // Điều kiện đóng: bảng tạm chỉ còn lô giữ cho phiếu chưa xong
        List<StockLot> after = stockLotRepository.findByWarehouseIdAndQtyRemainingGreaterThan(wip.getId(), BigDecimal.ZERO);
        Set<UUID> reservedIds = after.stream().map(StockLot::getReservedForRequestId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ProductionRequest> prs = productionRequestRepository.findAllById(reservedIds).stream()
                .collect(Collectors.toMap(ProductionRequest::getId, p -> p));
        for (StockLot l : after) {
            UUID r = l.getReservedForRequestId();
            if (r == null || !isHoldable(prs.get(r))) {
                throw new IllegalStateException(l.getItem().getName() + " còn dư ở bảng tạm: phải trả về kho, ghi hao hụt hoặc giữ cho phiếu SX chưa xong");
            }
        }

        s.setSessionStatus(SessionStatus.CLOSED);
        s.setClosedAt(Instant.now());
        s.setClosedBy(actorResolver.currentUsername());
        sessionRepository.save(s);
        return detail(s);
    }

    @Transactional
    public SessionInfo reopen(UUID id) {
        ProductionSession s = load(id);
        if (s.getSessionStatus() != SessionStatus.CLOSED) throw new IllegalStateException("Phiên ngày " + s.getSessionDate().format(DM) + " đang mở");
        boolean later = !sessionRepository.findByStoreIdAndSessionDateGreaterThanEqualOrderBySessionDateDesc(
                s.getStore().getId(), s.getSessionDate().plusDays(1)).isEmpty();
        if (later) throw new IllegalStateException("Đã có phiên ngày sau, không mở lại được phiên ngày " + s.getSessionDate().format(DM));
        s.setSessionStatus(SessionStatus.OPEN);
        s.setClosedAt(null);
        s.setClosedBy(null);
        return SessionInfo.from(sessionRepository.save(s));
    }

    // ── Đọc ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SessionInfo> list(LocalDate from, LocalDate to) {
        Store store = roles.workInProgress().getStore();
        LocalDate t = to != null ? to : WipDocs.today();
        LocalDate f = from != null ? from : t.minusDays(30);
        List<ProductionSession> list = sessionRepository.findByStoreIdAndSessionDateBetweenOrderBySessionDateDesc(store.getId(), f, t);
        Map<UUID, Integer> counts = new java.util.HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : requestRepository.countBySession(list.stream().map(ProductionSession::getId).toList())) {
                counts.put((UUID) row[0], ((Number) row[1]).intValue());
            }
        }
        return list.stream().map(s -> SessionInfo.from(s, counts.getOrDefault(s.getId(), 0))).toList();
    }

    @Transactional(readOnly = true)
    public SessionDetail get(UUID id) {
        return detail(load(id));
    }

    private SessionDetail detail(ProductionSession s) {
        boolean seeCost = scope.canSeeCost();
        List<Receipt> list = requestRepository.findBySessionIdOrderByCreatedAtAsc(s.getId()).stream()
                .map(e -> Receipt.from(e, seeCost)).sorted(java.util.Comparator.comparing(Receipt::sortKey)).toList();
        return SessionDetail.of(SessionInfo.from(s), list);
    }

    // ── Hỗ trợ ──────────────────────────────────────────────────────────────

    private ProductionSession load(UUID id) {
        return sessionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ProductionSession", id));
    }

    private void requireHoldable(UUID prId, String itemName) {
        ProductionRequest pr = productionRequestRepository.findById(prId)
                .orElseThrow(() -> new IllegalArgumentException(itemName + ": phiếu SX giữ không tồn tại"));
        if (!isHoldable(pr)) throw new IllegalStateException(itemName + ": phiếu " + pr.getCode() + " đã xong hoặc không còn hiệu lực, không giữ được");
    }

    /** Phiếu SX đã duyệt, còn hiệu lực và còn ít nhất một dòng chưa hoàn thành. */
    static boolean isHoldable(ProductionRequest pr) {
        return pr != null && pr.getApprovalStatus() == ApprovalStatus.APPROVED && pr.getStatus() == EntityStatus.ACTIVE
                && pr.getLines().stream().anyMatch(l -> l.getLineStatus() != ProductionLineStatus.COMPLETED);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
