package com.bakery.api.cake.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.bakery.api.cake.dto.CakeDesignRequest;
import com.bakery.api.cake.dto.CakeDesignResponse;
import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.entity.CakeDesignAccessory;
import com.bakery.api.cake.repository.CakeDesignRepository;
import com.bakery.api.cake.repository.DailyReportLineDesignRepository;
import com.bakery.api.cake.repository.DeliveryRecordDesignRepository;
import com.bakery.api.master.entity.CodeValue;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.CodeValueRepository;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.framework.entity.CommandAction;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CakeDesignService extends AbstractBakeryAdminService<CakeDesign, CakeDesignRequest, CakeDesignResponse> {

    public static final String GROUP_KEY = "CAKE_DESIGN_GROUP";

    private final CakeDesignRepository repository;
    private final ItemLookupRepository itemRepository;
    private final CodeValueRepository codeValueRepository;
    private final DeliveryRecordDesignRepository deliveryDesignRepository;
    private final com.bakery.api.cake.repository.DeliveryRecordAccessoryRepository deliveryAccessoryRepository;
    private final DailyReportLineDesignRepository reportDesignRepository;
    private final CommandRequestRepository commandRequestRepository;
    private final BakeryActorResolver actorResolver;

    @Override protected BaseRepository<CakeDesign> getRepository() { return repository; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "CakeDesign"; }
    @Override protected String entityLabel(CakeDesign e) { return e.getName(); }
    /** Admin khai báo danh mục, không qua luồng duyệt (spec chỉ yêu cầu CRUD). */
    @Override protected boolean isAutoApprove() { return true; }

    // ── Query ────────────────────────────────────────────────

    /** Filter: productId, groupCode, active, keyword (code/name). Mặc định sắp theo sortOrder tăng dần. */
    @Override
    public Page<CakeDesignResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        MultiValueMap<String, String> rest = new LinkedMultiValueMap<>(params);
        String productId = firstAndRemove(rest, "productId");
        String groupCode = firstAndRemove(rest, "groupCode");
        String active = firstAndRemove(rest, "active");
        String keyword = firstAndRemove(rest, "keyword");

        Specification<CakeDesign> spec = SpecificationBuilder.from(rest);
        if (productId != null) {
            UUID pid;
            try { pid = UUID.fromString(productId); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Sản phẩm không hợp lệ"); }
            spec = spec.and((root, q, cb) -> cb.equal(root.get("product").get("id"), pid));
        }
        if (groupCode != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("groupCode"), groupCode));
        }
        if (active != null) {
            if (!active.equalsIgnoreCase("true") && !active.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("active phải là true hoặc false");
            }
            boolean a = Boolean.parseBoolean(active);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("active"), a));
        }
        if (keyword != null) {
            String pattern = "%" + keyword.toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("code")), pattern),
                    cb.like(cb.lower(root.get("name")), pattern)));
        }
        if (!pageable.getSort().isSorted()) {
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                    Sort.by("sortOrder").ascending().and(Sort.by("name").ascending()));
        }
        Map<String, String> groups = groupNames();
        return repository.findAll(spec, pageable).map(e -> toResponse(e, groups));
    }

    private static String firstAndRemove(MultiValueMap<String, String> m, String key) {
        String v = m.getFirst(key);
        m.remove(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ── Mutate ───────────────────────────────────────────────

    @Override
    protected CakeDesign toEntity(CakeDesignRequest req) {
        CakeDesign e = new CakeDesign();
        apply(e, req);
        return e;
    }

    @Override
    protected void applyUpdate(CakeDesign e, CakeDesignRequest req) {
        apply(e, req);
    }

    private void apply(CakeDesign e, CakeDesignRequest req) {
        String code = req.code().trim();
        boolean dup = e.getId() == null ? repository.existsByCode(code) : repository.existsByCodeAndIdNot(code, e.getId());
        if (dup) throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã mẫu đã tồn tại: " + code);

        Item product = itemRepository.findById(req.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Item", req.productId()));
        if (itemRepository.findProductIdsIn(List.of(product.getId())).isEmpty()) {
            throw new IllegalArgumentException("Mẫu phải gắn với thành phẩm (PRODUCT): " + product.getCode());
        }
        if (req.groupCode() != null && !req.groupCode().isBlank() && !groupNames().containsKey(req.groupCode())) {
            throw new IllegalArgumentException("Nhóm mẫu không tồn tại: " + req.groupCode());
        }

        e.setCode(code);
        e.setName(req.name().trim());
        e.setProduct(product);
        e.setGroupCode(req.groupCode() == null || req.groupCode().isBlank() ? null : req.groupCode());
        if (req.active() != null) e.setActive(req.active());
        if (req.sortOrder() != null) e.setSortOrder(req.sortOrder());
        mergeAccessories(e, req.accessories() == null ? List.of() : req.accessories());
    }

    /** Thay toàn bộ phụ kiện. Sửa tại chỗ theo item để tránh đụng unique (design, item) khi flush. */
    private void mergeAccessories(CakeDesign e, List<CakeDesignRequest.Accessory> wanted) {
        Map<UUID, BigDecimal> qtyByItem = new LinkedHashMap<>();
        for (CakeDesignRequest.Accessory a : wanted) {
            if (a.qty().signum() <= 0) throw new IllegalArgumentException("Số lượng phụ kiện phải lớn hơn 0");
            if (qtyByItem.put(a.itemId(), a.qty()) != null) {
                throw new IllegalArgumentException("Phụ kiện bị trùng trong danh sách");
            }
        }
        Map<UUID, Item> items = itemRepository.findAllById(qtyByItem.keySet()).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        for (UUID id : qtyByItem.keySet()) {
            if (!items.containsKey(id)) throw new ResourceNotFoundException("Item", id);
        }
        Set<UUID> products = new HashSet<>(itemRepository.findProductIdsIn(qtyByItem.keySet()));
        for (UUID id : qtyByItem.keySet()) {
            if (products.contains(id)) {
                throw new IllegalArgumentException("Phụ kiện không được là thành phẩm: " + items.get(id).getCode());
            }
        }

        e.getAccessories().removeIf(a -> !qtyByItem.containsKey(a.getItem().getId()));
        Map<UUID, CakeDesignAccessory> existing = e.getAccessories().stream()
                .collect(Collectors.toMap(a -> a.getItem().getId(), Function.identity()));
        for (Map.Entry<UUID, BigDecimal> en : qtyByItem.entrySet()) {
            CakeDesignAccessory acc = existing.get(en.getKey());
            if (acc == null) {
                acc = new CakeDesignAccessory();
                acc.setCakeDesign(e);
                acc.setItem(items.get(en.getKey()));
                e.getAccessories().add(acc);
            }
            acc.setQty(en.getValue());
        }
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CakeDesign e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(getEntityName(), id));
        if (deliveryDesignRepository.existsByCakeDesignId(id) || reportDesignRepository.existsByCakeDesignId(id)
                || deliveryAccessoryRepository.existsByCakeDesignId(id)) {
            throw new IllegalStateException("Mẫu đã dùng, hãy ngừng dùng thay vì xóa");
        }
        String label = entityLabel(e);
        repository.delete(e);
        log(CommandAction.DELETE, id, label, null);
    }

    // ── Response ─────────────────────────────────────────────

    @Override
    protected CakeDesignResponse toResponse(CakeDesign e) {
        return toResponse(e, groupNames());
    }

    private CakeDesignResponse toResponse(CakeDesign e, Map<String, String> groups) {
        CakeDesignResponse r = new CakeDesignResponse();
        r.applyFrom(e);
        r.setCode(e.getCode());
        r.setName(e.getName());
        r.setProductId(e.getProduct().getId());
        r.setProduct(new ReferenceValue(e.getProduct().getCode(), e.getProduct().getName()));
        r.setGroupCode(e.getGroupCode());
        if (e.getGroupCode() != null) {
            r.setGroup(new ReferenceValue(e.getGroupCode(), groups.getOrDefault(e.getGroupCode(), e.getGroupCode())));
        }
        r.setImageUrl(e.getImageUrl());
        r.setActive(e.isActive());
        r.setSortOrder(e.getSortOrder());
        List<CakeDesignResponse.AccessoryResponse> acc = new ArrayList<>();
        for (CakeDesignAccessory a : e.getAccessories().stream()
                .sorted(java.util.Comparator.comparing(x -> x.getItem().getCode())).toList()) {
            CakeDesignResponse.AccessoryResponse ar = new CakeDesignResponse.AccessoryResponse();
            ar.setItemId(a.getItem().getId());
            ar.setItem(new ReferenceValue(a.getItem().getCode(), a.getItem().getName()));
            ar.setQty(a.getQty());
            ar.setUnit(a.getItem().getUnit());
            acc.add(ar);
        }
        r.setAccessories(acc);
        return r;
    }

    public Map<String, String> groupNameMap() { return groupNames(); }

    /** code → tên của nhóm mẫu (code_value CAKE_DESIGN_GROUP). */
    private Map<String, String> groupNames() {
        return codeValueRepository.findByGroupKeyAndStatusOrderBySortOrderAsc(GROUP_KEY, EntityStatus.ACTIVE).stream()
                .collect(Collectors.toMap(CodeValue::getCode, CodeValue::getName, (a, b) -> a, LinkedHashMap::new));
    }
}
