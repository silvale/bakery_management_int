package com.bakery.api.master.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.master.dto.StoreRequest;
import com.bakery.api.master.dto.StoreResponse;
import com.bakery.api.master.entity.Store;
import com.bakery.api.master.repository.StoreRepository;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

/** 23/BE-1: cửa hàng. Không luồng duyệt; xóa là xóa thật (mã dùng lại được), chặn khi còn kho. */
@Service
@RequiredArgsConstructor
public class StoreService extends AbstractBakeryAdminService<Store, StoreRequest, StoreResponse> {

    private final StoreRepository repository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<Store> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Store"; }
    @Override protected boolean isAutoApprove() { return true; }

    /** Mã không đổi sau khi tạo. */
    static String codeBlocker(Store e, StoreRequest req) {
        return e.getCode().equals(req.code().trim()) ? null : "Không đổi được mã cửa hàng";
    }

    /** Lý do không xóa được cửa hàng, null nếu xóa được. */
    static String deleteBlocker(long warehouses) {
        return warehouses > 0
                ? "Cửa hàng còn " + warehouses + " kho, chuyển kho sang cửa hàng khác trước khi xóa" : null;
    }

    @Override
    protected Store toEntity(StoreRequest req) {
        String code = req.code().trim();
        if (repository.existsByCode(code)) throw new IllegalStateException("Mã cửa hàng " + code + " đã tồn tại");
        Store e = new Store();
        e.setCode(code);
        applyUpdate(e, req);
        return e;
    }

    @Override
    protected void applyUpdate(Store e, StoreRequest req) {
        e.setName(req.name().trim());
        if (req.sortOrder() != null) e.setSortOrder(req.sortOrder());
    }

    @Override
    @Transactional
    public StoreResponse update(UUID id, StoreRequest req) {
        Store e = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        String blocked = codeBlocker(e, req);
        if (blocked != null) throw new IllegalStateException(blocked);
        return super.update(id, req);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Store e = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        String blocked = deleteBlocker(repository.countWarehouses(id));
        if (blocked != null) throw new IllegalStateException(blocked);
        repository.delete(e);
    }

    // ── Response + số kho ───────────────────────────────────────────────────

    @Override
    public Page<StoreResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        Map<UUID, Long> counts = counts();
        if (pageable.getSort().isUnsorted()) pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), org.springframework.data.domain.Sort.by("sortOrder", "code"));
        return repository.findAll(Specification.where(null), pageable).map(e -> toResponse(e, counts));
    }

    @Override
    public List<StoreResponse> findAll() {
        Map<UUID, Long> counts = counts();
        return repository.findAll().stream()
                .sorted(Comparator.comparingInt(Store::getSortOrder).thenComparing(Store::getCode))
                .map(e -> toResponse(e, counts)).toList();
    }

    private Map<UUID, Long> counts() {
        Map<UUID, Long> m = new HashMap<>();
        for (Object[] r : repository.countWarehousesByStore()) m.put((UUID) r[0], ((Number) r[1]).longValue());
        return m;
    }

    @Override
    protected StoreResponse toResponse(Store e) {
        return toResponse(e, Map.of(e.getId(), repository.countWarehouses(e.getId())));
    }

    private StoreResponse toResponse(Store e, Map<UUID, Long> counts) {
        StoreResponse r = new StoreResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setSortOrder(e.getSortOrder());
        r.setWarehouseCount(counts.getOrDefault(e.getId(), 0L));
        return r;
    }
}
