package com.bakery.api.master.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.auth.service.AccessScope;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.util.SpecificationBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.MultiValueMap;
import com.bakery.api.master.dto.WarehouseRequest;
import com.bakery.api.master.dto.WarehouseResponse;
import com.bakery.api.master.entity.Warehouse;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import com.bakery.framework.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService extends AbstractBakeryAdminService<Warehouse, WarehouseRequest, WarehouseResponse> {

    private final WarehouseRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<Warehouse> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Warehouse"; }

    /** 4 kho mặc định mà sản xuất, giao hàng, báo cáo tìm theo code — đổi code/loại hoặc xóa là hỏng luồng. */
    static final Set<String> DEFAULT_CODES = Set.of("MAIN", "KITCHEN", "SHOP", "COLD");

    /** Lý do không cho sửa kho mặc định, null nếu được. Vẫn sửa được tên và địa chỉ. */
    static String updateBlocker(Warehouse e, WarehouseRequest req) {
        if (!DEFAULT_CODES.contains(e.getCode())) return null;
        if (!e.getCode().equals(req.code()) || e.getWarehouseType() != req.warehouseType()) {
            return "Kho mặc định " + e.getCode() + " không được đổi mã hoặc loại kho";
        }
        return null;
    }

    @Override
    @Transactional
    public WarehouseResponse update(UUID id, WarehouseRequest request) {
        Warehouse e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        String blocked = updateBlocker(e, request);
        if (blocked != null) throw new IllegalStateException(blocked);
        return super.update(id, request);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Warehouse e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        if (DEFAULT_CODES.contains(e.getCode())) {
            throw new IllegalStateException("Kho mặc định " + e.getCode() + " không được xóa");
        }
        super.delete(id);
    }

    @Override
    protected Warehouse toEntity(WarehouseRequest req) {
        Warehouse e = new Warehouse();
        e.setCode(req.code());
        e.setName(req.name());
        e.setWarehouseType(req.warehouseType());
        e.setAddress(req.address());
        return e;
    }

    @Override
    protected void applyUpdate(Warehouse e, WarehouseRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setWarehouseType(req.warehouseType());
        e.setAddress(req.address());
    }

    private final AccessScope scope;

    // ── 11/D2: user chỉ thấy các kho được gán (Admin: tất cả) ──
    private Specification<Warehouse> inScope() {
        if (scope.isAdmin()) return (r, q, cb) -> cb.conjunction();
        Set<UUID> ids = scope.warehouseIds();
        return (r, q, cb) -> ids.isEmpty() ? cb.disjunction() : r.get("id").in(ids);
    }

    @Override
    public Page<WarehouseResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        Specification<Warehouse> spec = SpecificationBuilder.from(params);
        return repository.findAll(spec.and(inScope()), pageable).map(this::toResponse);
    }

    @Override
    public List<WarehouseResponse> findAll() {
        return repository.findAll(inScope().and((r, q, cb) -> cb.equal(r.get("status"), EntityStatus.ACTIVE)))
                .stream().map(this::toResponse).toList();
    }

    @Override
    public Optional<WarehouseResponse> findById(UUID id) {
        return repository.findById(id).map(e -> {
            scope.assertWarehouse(e);
            return toResponse(e);
        });
    }

    /**
     * Mọi kho đang dùng — chỉ {id, code, name, type}, để chọn KHO XUẤT của phiếu điều chuyển (bếp xin hàng từ Kho Tổng).
     * Không dùng để xem dữ liệu kho: mọi API dữ liệu vẫn kiểm theo kho được gán.
     */
    public List<com.bakery.api.master.dto.WarehouseRefResponse> allForTransfer() {
        return repository.findAllByStatus(EntityStatus.ACTIVE).stream()
                .sorted(java.util.Comparator.comparing(Warehouse::getCode))
                .map(w -> new com.bakery.api.master.dto.WarehouseRefResponse(w.getId(), w.getCode(), w.getName(),
                        w.getWarehouseType() != null ? w.getWarehouseType().name() : null))
                .toList();
    }

    @Override
    protected WarehouseResponse toResponse(Warehouse e) {
        WarehouseResponse r = new WarehouseResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setWarehouseType(e.getWarehouseType());
        r.setAddress(e.getAddress());
        return r;
    }
}
