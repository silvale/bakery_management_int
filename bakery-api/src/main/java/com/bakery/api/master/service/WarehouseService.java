package com.bakery.api.master.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bakery.api.auth.service.AccessScope;
import com.bakery.api.master.dto.WarehouseRefResponse;
import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.dto.WarehouseResponse;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.entity.WarehouseKind;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import com.bakery.framework.util.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

/**
 * Quản lý kho theo cấu hình (22). Quy tắc sửa/xóa ở thiết kế §5.3; mỗi quy tắc là hàm tĩnh trả lý do hoặc null
 * để unit test không cần Spring.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService extends AbstractBakeryAdminService<Warehouse, WarehouseRequest, WarehouseResponse> {

    private final WarehouseRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;
    private final AccessScope scope;

    @Override protected BaseRepository<Warehouse> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Warehouse"; }

    // ── Trạng thái sau khi áp request (cờ null = giữ nguyên) ────────────────

    /** Cấu hình kho sau khi sửa. */
    record Config(WarehouseKind kind, boolean receive, boolean production, boolean semi,
                  boolean hold, boolean sales, boolean export) {

        static Config of(Warehouse e) {
            return new Config(e.getKind(), e.isCanReceivePurchase(), e.isProductionSource(), e.isCanStoreSemi(),
                    e.isDeliveryHold(), e.isSalesPoint(), e.isCanExportExternal());
        }

        /** Áp request lên cấu hình hiện có; trường null giữ nguyên. */
        static Config apply(Config c, WarehouseRequest r) {
            return new Config(
                    r.kind() != null ? r.kind() : c.kind,
                    r.canReceivePurchase() != null ? r.canReceivePurchase() : c.receive,
                    r.isProductionSource() != null ? r.isProductionSource() : c.production,
                    r.canStoreSemi() != null ? r.canStoreSemi() : c.semi,
                    r.isDeliveryHold() != null ? r.isDeliveryHold() : c.hold,
                    r.isSalesPoint() != null ? r.isSalesPoint() : c.sales,
                    r.canExportExternal() != null ? r.canExportExternal() : c.export);
        }

        boolean anyRole() { return receive || production || semi || hold || sales || export; }
    }

    /** Số liệu từ DB mà quy tắc cần. */
    record Facts(boolean hasStockOrRequests, long ingredientsHere, long semiHere,
                 boolean otherHasProduction, boolean otherHasReceive) {}

    // ── Quy tắc (thiết kế §5.3) ─────────────────────────────────────────────

    /** Mã không đổi được với mọi kho. */
    static String codeBlocker(Warehouse e, WarehouseRequest req) {
        return e.getCode().equals(req.code()) ? null : "Không đổi được mã kho";
    }

    /** Không đổi loại khi kho đã có lô tồn hoặc phiếu. */
    static String kindBlocker(Config before, Config after, Facts f) {
        if (before.kind() != after.kind() && f.hasStockOrRequests()) {
            return "Không đổi được loại kho vì kho đã có lô tồn hoặc phiếu";
        }
        return null;
    }

    /** Nơi nhận ngoài không làm việc gì. */
    static String externalBlocker(Config after) {
        return after.kind() == WarehouseKind.EXTERNAL && after.anyRole()
                ? "Kho nơi nhận ngoài không được bật việc nào" : null;
    }

    /** Tắt rời Bán hàng / Bánh chờ giao: chỉ chuyển sang kho khác, không tắt (kho đang giữ việc mà tắt đi). */
    static String singletonOffBlocker(Config before, Config after) {
        if (before.sales() && !after.sales()) return "Không tắt được Bán hàng; hãy chuyển việc này sang kho khác";
        if (before.hold() && !after.hold()) return "Không tắt được Bánh chờ giao; hãy chuyển việc này sang kho khác";
        return null;
    }

    /** Không tắt Sản xuất lấy NL / Lưu BTP khi còn mặt hàng trỏ tới; phải còn ít nhất một kho mỗi việc. */
    static String roleOffBlocker(Config before, Config after, Facts f) {
        if (before.production() && !after.production()) {
            if (f.ingredientsHere() > 0) {
                return "Không tắt được Sản xuất lấy NL: còn " + f.ingredientsHere() + " nguyên liệu dùng kho này làm kho lưu trữ";
            }
            if (!f.otherHasProduction()) return "Phải còn ít nhất một kho Sản xuất lấy NL";
        }
        if (before.semi() && !after.semi() && f.semiHere() > 0) {
            return "Không tắt được Lưu BTP: còn " + f.semiHere() + " bán thành phẩm có kho mặc định là kho này";
        }
        if (before.receive() && !after.receive() && !f.otherHasReceive()) {
            return "Phải còn ít nhất một kho Nhận NCC";
        }
        return null;
    }

    /** Lý do không xóa được kho, null nếu xóa được. */
    static String deleteBlocker(Config c, long stockLots, long itemsHere, boolean otherHasProduction, boolean otherHasReceive) {
        if (stockLots > 0) return "Không xóa được kho: còn " + stockLots + " lô tồn";
        if (itemsHere > 0) return "Không xóa được kho: còn " + itemsHere + " mặt hàng dùng làm kho mặc định / kho lưu trữ";
        if (c.sales()) return "Không xóa được kho đang là kho Bán hàng; chuyển việc này sang kho khác trước";
        if (c.hold()) return "Không xóa được kho đang là kho Bánh chờ giao; chuyển việc này sang kho khác trước";
        if (c.production() && !otherHasProduction) return "Phải còn ít nhất một kho Sản xuất lấy NL";
        if (c.receive() && !otherHasReceive) return "Phải còn ít nhất một kho Nhận NCC";
        return null;
    }

    /** Chạy lần lượt các quy tắc, ném lỗi nghiệp vụ ở lý do đầu tiên. */
    static String updateBlocker(Warehouse e, WarehouseRequest req, Facts f) {
        Config before = Config.of(e);
        Config after = Config.apply(before, req);
        String m = codeBlocker(e, req);
        if (m == null) m = kindBlocker(before, after, f);
        if (m == null) m = externalBlocker(after);
        if (m == null) m = singletonOffBlocker(before, after);
        if (m == null) m = roleOffBlocker(before, after, f);
        return m;
    }

    // ── Sửa / xóa / tạo ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        Warehouse e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        Config before = Config.of(e);
        Config after = Config.apply(before, request);
        // Chỉ truy vấn số liệu khi quy tắc cần (đổi loại / tắt việc)
        Facts f = factsFor(e, before, after);
        String blocked = updateBlocker(e, request, f);
        if (blocked != null) throw new IllegalStateException(blocked);
        transferSingletons(e, after);
        return super.update(id, request);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Warehouse e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        List<Warehouse> others = activeOthers(e);
        String blocked = deleteBlocker(Config.of(e), repository.countStockLots(id),
                repository.countItemsByDefaultWarehouse(id),
                others.stream().anyMatch(Warehouse::isProductionSource),
                others.stream().anyMatch(Warehouse::isCanReceivePurchase));
        if (blocked != null) throw new IllegalStateException(blocked);
        super.delete(id);
    }

    @Override
    protected Warehouse toEntity(WarehouseRequest req) {
        Warehouse e = new Warehouse();
        e.setCode(req.code());
        applyFields(e, req);
        return e;
    }

    @Override
    protected void beforeCreate(Warehouse e) {
        Config c = Config.of(e);
        String m = externalBlocker(c);
        if (m != null) throw new IllegalStateException(m);
        transferSingletons(e, c);
    }

    @Override
    protected void applyUpdate(Warehouse e, WarehouseRequest req) {
        applyFields(e, req);   // mã không đổi (đã kiểm ở update)
    }

    private void applyFields(Warehouse e, WarehouseRequest req) {
        Config c = Config.apply(Config.of(e), req);
        e.setName(req.name());
        e.setAddress(req.address());
        e.setKind(c.kind() != null ? c.kind() : WarehouseKind.INTERNAL);
        e.setCanReceivePurchase(c.receive());
        e.setProductionSource(c.production());
        e.setCanStoreSemi(c.semi());
        e.setDeliveryHold(c.hold());
        e.setSalesPoint(c.sales());
        e.setCanExportExternal(c.export());
        if (req.sortOrder() != null) e.setSortOrder(req.sortOrder());
    }

    /** Bật Bán hàng / Bánh chờ giao cho kho này thì tắt ở kho đang giữ, trong cùng transaction (flush trước vì unique index). */
    void transferSingletons(Warehouse target, Config after) {
        if (after.sales() && !target.isSalesPoint()) clearHolder(target, true);
        if (after.hold() && !target.isDeliveryHold()) clearHolder(target, false);
    }

    private void clearHolder(Warehouse target, boolean sales) {
        for (Warehouse w : repository.findAll()) {
            if (w == target || (target.getId() != null && target.getId().equals(w.getId()))) continue;
            if (sales && w.isSalesPoint()) { w.setSalesPoint(false); repository.saveAndFlush(w); }
            if (!sales && w.isDeliveryHold()) { w.setDeliveryHold(false); repository.saveAndFlush(w); }
        }
    }

    private List<Warehouse> activeOthers(Warehouse e) {
        return repository.findAllByStatus(EntityStatus.ACTIVE).stream()
                .filter(w -> !w.getId().equals(e.getId())).toList();
    }

    private Facts factsFor(Warehouse e, Config before, Config after) {
        boolean kindChange = before.kind() != after.kind();
        boolean prodOff = before.production() && !after.production();
        boolean semiOff = before.semi() && !after.semi();
        boolean recvOff = before.receive() && !after.receive();
        List<Warehouse> others = (prodOff || recvOff) ? activeOthers(e) : List.of();
        return new Facts(
                kindChange && (repository.hasAnyStockLot(e.getId()) || repository.hasAnyRequest(e.getId())),
                prodOff ? repository.countItemsByDefaultWarehouseAndType(e.getId(), "INGREDIENT") : 0,
                semiOff ? repository.countItemsByDefaultWarehouseAndType(e.getId(), "SEMI_PRODUCT") : 0,
                others.stream().anyMatch(Warehouse::isProductionSource),
                others.stream().anyMatch(Warehouse::isCanReceivePurchase));
    }

    // ── 11/D2: user chỉ thấy các kho được gán (Admin: tất cả) ──────────────

    private Specification<Warehouse> inScope() {
        if (scope.isAdmin()) return (r, q, cb) -> cb.conjunction();
        java.util.Set<UUID> ids = scope.warehouseIds();
        return (r, q, cb) -> ids.isEmpty() ? cb.disjunction() : r.get("id").in(ids);
    }

    private static final Sort DEFAULT_SORT = Sort.by("sortOrder", "code");

    @Override
    public Page<WarehouseResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        Specification<Warehouse> spec = SpecificationBuilder.from(params);
        if (pageable.getSort().isUnsorted()) pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        Page<Warehouse> page = repository.findAll(spec.and(inScope()), pageable);
        Counts counts = countsIfAllowed();
        return page.map(w -> toResponse(w, counts));
    }

    @Override
    public List<WarehouseResponse> findAll() {
        Counts counts = countsIfAllowed();
        return repository.findAll(inScope().and((r, q, cb) -> cb.equal(r.get("status"), EntityStatus.ACTIVE)), DEFAULT_SORT)
                .stream().map(w -> toResponse(w, counts)).toList();
    }

    @Override
    public Optional<WarehouseResponse> findById(UUID id) {
        return repository.findById(id).map(e -> {
            scope.assertWarehouse(e);
            return toResponse(e, countsIfAllowed());
        });
    }

    /**
     * Mọi kho nội bộ đang dùng — chỉ {id, code, name, kind}, để chọn KHO XUẤT của phiếu điều chuyển (bếp xin hàng từ Kho Chính).
     * Không dùng để xem dữ liệu kho: mọi API dữ liệu vẫn kiểm theo kho được gán. Nơi nhận ngoài không có tồn và không làm kho phiếu.
     */
    public List<WarehouseRefResponse> allForTransfer() {
        return repository.findAllByStatus(EntityStatus.ACTIVE).stream()
                .filter(w -> !w.isExternal())
                .sorted(Comparator.comparingInt(Warehouse::getSortOrder).thenComparing(Warehouse::getCode))
                .map(w -> new WarehouseRefResponse(w.getId(), w.getCode(), w.getName(),
                        w.getKind() != null ? w.getKind().name() : null))
                .toList();
    }

    // ── Response + số đếm (chỉ khi có WAREHOUSES:VIEW) ──────────────────────

    record Counts(Map<UUID, Long> lots, Map<UUID, Long> users, Map<UUID, Long> items) {}

    private Counts countsIfAllowed() {
        if (!scope.can("WAREHOUSES", "VIEW")) return null;
        return new Counts(toMap(repository.countStockLotsByWarehouse()), toMap(repository.countUsersByWarehouse()),
                toMap(repository.countItemsByWarehouse()));
    }

    private static Map<UUID, Long> toMap(List<Object[]> rows) {
        Map<UUID, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put((UUID) r[0], ((Number) r[1]).longValue());
        return m;
    }

    @Override
    protected WarehouseResponse toResponse(Warehouse e) {
        return toResponse(e, countsIfAllowed());
    }

    private WarehouseResponse toResponse(Warehouse e, Counts counts) {
        WarehouseResponse r = new WarehouseResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setAddress(e.getAddress());
        r.setKind(e.getKind());
        r.setCanReceivePurchase(e.isCanReceivePurchase());
        r.setProductionSource(e.isProductionSource());
        r.setCanStoreSemi(e.isCanStoreSemi());
        r.setDeliveryHold(e.isDeliveryHold());
        r.setSalesPoint(e.isSalesPoint());
        r.setCanExportExternal(e.isCanExportExternal());
        r.setSortOrder(e.getSortOrder());
        if (counts != null) {
            r.setStockLotCount(counts.lots().getOrDefault(e.getId(), 0L));
            r.setUserCount(counts.users().getOrDefault(e.getId(), 0L));
            r.setItemCount(counts.items().getOrDefault(e.getId(), 0L));
        }
        return r;
    }
}
