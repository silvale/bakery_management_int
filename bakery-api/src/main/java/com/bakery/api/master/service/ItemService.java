/*
 * Copyright (c) 2024 Bakery Management System
 */
package com.bakery.api.master.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.bakery.api.master.dto.ItemRequest;
import com.bakery.api.master.dto.ItemBySupplierResponse;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.entity.ItemPackaging;
import com.bakery.api.master.entity.Product;
import com.bakery.api.master.entity.ProductMapping;
import com.bakery.api.master.entity.ProductExpiryConfig;
import com.bakery.api.master.entity.SemiProduct;
import com.bakery.api.master.entity.Supplier;
import com.bakery.api.master.repository.ItemLookupRepository;
import com.bakery.api.master.repository.ItemPackagingRepository;
import com.bakery.api.master.repository.ProductMappingRepository;
import com.bakery.api.master.repository.ProductExpiryConfigRepository;
import com.bakery.api.master.repository.SupplierRepository;
import com.bakery.api.master.repository.WarehouseRepository;
import com.bakery.api.pricing.entity.IngredientPrice;
import com.bakery.api.pricing.repository.IngredientPriceRepository;
import com.bakery.api.production.entity.ItemGroup;
import com.bakery.api.production.repository.ItemGroupRepository;
import com.bakery.api.recipe.dto.RecipeLineRequest;
import com.bakery.api.recipe.entity.Recipe;
import com.bakery.api.recipe.entity.RecipeLine;
import com.bakery.api.recipe.repository.RecipeRepository;
import com.bakery.api.recipe.service.RecipeService;
import com.bakery.framework.entity.ApprovalStatus;
import com.bakery.framework.entity.EntityStatus;
import com.bakery.framework.exception.ResourceNotFoundException;
import com.bakery.framework.metadata.ReferenceValue;
import com.bakery.framework.repository.BaseRepository;
import com.bakery.framework.repository.CommandRequestRepository;
import com.bakery.framework.security.BakeryActorResolver;
import com.bakery.framework.service.AbstractBakeryAdminService;
import com.bakery.framework.util.SpecificationBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Service thống nhất cho mọi loại Item (INGREDIENT, SEMI_PRODUCT, PRODUCT).
 *
 * <p>Extend {@link AbstractBakeryAdminService} với generic type {@code Item} — đúng pattern
 * của toàn bộ hệ thống. Logic phân loại (toEntity / applyUpdate) dùng switch/instanceof
 * vì mapping là polymorphic và không thể delegate cho MapStruct.
 *
 * <p>Recipe bundling (PRODUCT + SEMI_PRODUCT) được xử lý trong lifecycle hooks:
 * afterCreate → afterUpdate → afterApprove → beforeDelete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService extends AbstractBakeryAdminService<Item, ItemRequest, ItemResponse> {

    private final ItemLookupRepository repository;
    private final SupplierRepository supplierRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ItemCostHelper itemCostHelper;
    private final ItemGroupRepository itemGroupRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;
    private final ItemPackagingRepository packagingRepository;
    private final com.bakery.api.master.repository.UnitRepository unitRepository;
    private final ProductMappingRepository productMappingRepository;
    private final BakeryActorResolver actorResolver;
    private final CommandRequestRepository commandRequestRepository;
    private final com.bakery.api.auth.service.AccessScope scope;

    /**
     * ThreadLocal chuyển ItemRequest xuống lifecycle hooks (afterCreate / afterUpdate).
     * Luôn được clear trong finally để tránh memory leak.
     */
    private final ThreadLocal<ItemRequest> currentRequest = new ThreadLocal<>();

    /**
     * ThreadLocal giữ price map khi list items — tránh N+1 query ingredient_price.
     * Set trước khi map toResponse(), clear trong finally.
     */
    private final ThreadLocal<Map<UUID, IngredientPrice>> priceMapLocal = new ThreadLocal<>();

    // ── Framework wiring ──────────────────────────────────────────────────────

    @Override protected BaseRepository<Item> getRepository() { return repository; }
    @Override public Class<Item> getEntityClass() { return Item.class; }
    @Override protected BakeryActorResolver getActorResolver() { return actorResolver; }
    @Override protected CommandRequestRepository getCommandRequestRepository() { return commandRequestRepository; }
    @Override protected String getEntityName() { return "Item"; }
    @Override protected String entityLabel(com.bakery.api.master.entity.Item e) { return e.getName() != null ? e.getName() : e.getCode(); }

    // ── Override findAll — xử lý discriminator spec ───────────────────────────

    /**
     * Hỗ trợ filter theo {@code ?itemType=INGREDIENT|SEMI_PRODUCT|PRODUCT} bằng cách tách
     * param này ra trước, build JPA discriminator predicate ({@code root.type()}), rồi AND
     * với Specification còn lại từ SpecificationBuilder.
     */
    @Override
    public Page<ItemResponse> findAll(MultiValueMap<String, String> params, Pageable pageable) {
        String itemType = params.getFirst("itemType");

        MultiValueMap<String, String> rest = new LinkedMultiValueMap<>(params);
        rest.remove("itemType");

        // q = full-text OR search trên name + code (case-insensitive).
        // Loại khỏi rest để SpecificationBuilder không hiểu nhầm thành exact-match field.
        String q = rest.getFirst("q");
        rest.remove("q");

        // 21: filter không phải cột → tách khỏi SpecificationBuilder (nó nuốt im lặng param lạ)
        String shelfMin = rest.getFirst("shelfDaysMin");
        String shelfMax = rest.getFirst("shelfDaysMax");
        String hasRecipe = rest.getFirst("hasRecipe");
        rest.remove("shelfDaysMin");
        rest.remove("shelfDaysMax");
        rest.remove("hasRecipe");

        Specification<Item> spec = SpecificationBuilder.from(rest);

        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("code")), pattern)
            ));
        }

        if (itemType != null && !itemType.isBlank()) {
            Class<? extends Item> typeClass = resolveClass(itemType);
            // 19/D5: hỏi thẳng tab PRODUCT/INGREDIENT mà không có quyền tab → 403 (BTP giữ như 17: rỗng)
            if (typeClass != SemiProduct.class && !scope.canViewItemType(itemType)) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "Không có quyền xem tab này [" + com.bakery.api.auth.service.AccessScope.itemTypeScreen(itemType) + ":VIEW]");
            }
            spec = spec.and((root, query, cb) -> cb.equal(root.type(), typeClass));
        }

        spec = spec.and(shelfDaysSpec(itemType, shelfMin, shelfMax)).and(hasRecipeSpec(itemType, hasRecipe));

        // 19/D2: chỉ trả các loại có quyền tab (AND thêm nên không lách được bằng param). BTP: 17 → 19 đổi sang ITEMS_SEMI
        for (String t : List.of("PRODUCT", "SEMI_PRODUCT", "INGREDIENT")) {
            if (!scope.canViewItemType(t)) {
                Class<? extends Item> hidden = resolveClass(t);
                spec = spec.and((root, query, cb) -> cb.notEqual(root.type(), hidden));
            }
        }

        // sort=<field>,<asc|desc>: tự áp ORDER BY trong spec (null cuối, id làm tiêu chí phụ).
        // Không truyền sort → giữ nguyên hành vi cũ.
        if (pageable.getSort().isSorted()) {
            validateSort(pageable.getSort());
            spec = spec.and(sortSpec(pageable.getSort()));
            pageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }

        Page<Item> page = repository.findAll(spec, pageable);

        // Batch-fetch latest price cho tất cả ingredients trong page — tránh N+1
        List<UUID> ingredientIds = page.getContent().stream()
                .filter(i -> i instanceof Ingredient)
                .map(Item::getId)
                .toList();
        if (!ingredientIds.isEmpty()) {
            Map<UUID, IngredientPrice> priceMap = ingredientPriceRepository
                    .findLatestByItemIds(ingredientIds)
                    .stream()
                    .collect(Collectors.toMap(p -> p.getItem().getId(), p -> p));
            priceMapLocal.set(priceMap);
        }

        try {
            return page.map(this::toResponse);
        } finally {
            priceMapLocal.remove();
        }
    }

    /** /items/all — không có ITEMS_SEMI:VIEW thì bỏ BTP (17 → 19); PRODUCT/INGREDIENT không lọc (19/D5, dropdown vận hành). */
    @Override
    public List<ItemResponse> findAll() {
        boolean semi = scope.canViewItemType("SEMI_PRODUCT");
        return repository.findAllByStatus(EntityStatus.ACTIVE).stream()
                .filter(i -> semi || !isSemi(i))
                .map(this::toResponse)
                .toList();
    }

    /** /items/{id} của BTP → 403 khi không có ITEMS_SEMI:VIEW (17 → 19). */
    @Override
    public Optional<ItemResponse> findById(UUID id) {
        return repository.findById(id).map(i -> { requireVisible(i); return toResponse(i); });
    }

    /** Dùng cho history/diff: cùng quy tắc, không lộ BTP qua bản sửa đổi cũ. */
    @Override
    public ItemResponse mapToResponse(Object entity) {
        if (entity instanceof Item i) requireVisible(i);
        return super.mapToResponse(entity);
    }

    private static boolean isSemi(Item i) {
        return org.hibernate.Hibernate.unproxy(i) instanceof SemiProduct;
    }

    private void requireVisible(Item i) {
        if (isSemi(i) && !scope.canViewItemType("SEMI_PRODUCT")) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Không có quyền xem bán thành phẩm [ITEMS_SEMI:VIEW]");
        }
    }

    /**
     * 12/BE-1: mặt hàng thuộc NCC (mặc định hoặc có trong bảng giá), không kèm giá. NCC không tồn tại → 404.
     * Sắp theo tên (collation tiếng Việt).
     */
    @Transactional(readOnly = true)
    public List<ItemBySupplierResponse> findBySupplier(UUID supplierId) {
        supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", supplierId));
        Map<UUID, String> matched = new java.util.LinkedHashMap<>();
        for (Object[] row : repository.findIdsBySupplier(supplierId)) {
            matched.put((UUID) row[0], (String) row[1]);
        }
        if (matched.isEmpty()) return List.of();
        // Nạp item TRƯỚC quy cách: ItemPackaging.item là proxy lazy, nạp trước sẽ khiến findAllById trả proxy (instanceof Ingredient sai)
        boolean semiOk = scope.canViewItemType("SEMI_PRODUCT");   // 17/D1 → 19
        List<Item> loaded = repository.findAllById(matched.keySet()).stream()
                .filter(i -> semiOk || !isSemi(i)).toList();
        Map<UUID, List<ItemResponse.PackagingResponse>> packs = new java.util.HashMap<>();
        List<ItemPackaging> rows = packagingRepository.findByItemIdInOrderByQtyPerPackAsc(matched.keySet());
        Set<UUID> inUse = packagingInUse(rows.stream().map(ItemPackaging::getId).toList());
        rows.forEach(p -> packs.computeIfAbsent(p.getItem().getId(), k -> new java.util.ArrayList<>())
                .add(toPackagingResponse(p, inUse)));
        java.text.Collator vi = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("vi"));
        return loaded.stream().map(i0 -> {
            Item i = org.hibernate.Hibernate.unproxy(i0, Item.class) instanceof Item real ? real : i0;
            ItemBySupplierResponse r = new ItemBySupplierResponse();
            r.setId(i.getId());
            r.setCode(i.getCode());
            r.setName(i.getName());
            r.setUnit(i.getUnit());
            r.setImageUrl(i.getImageUrl());
            r.setItemType(i instanceof SemiProduct ? "SEMI_PRODUCT" : "INGREDIENT");
            if (i.getItemGroup() != null) {
                r.setItemGroup(new ReferenceValue(i.getItemGroup().getCode(), i.getItemGroup().getName()));
            }
            if (i instanceof Ingredient ing && ing.getDefaultSupplier() != null) {
                r.setDefaultSupplier(new ReferenceValue(ing.getDefaultSupplier().getCode(), ing.getDefaultSupplier().getName()));
            }
            r.setPackagings(packs.getOrDefault(i.getId(), List.of()));
            r.setMatchedBy(matched.get(i.getId()));
            return r;
        }).sorted(java.util.Comparator.comparing(ItemBySupplierResponse::getName, vi)).toList();
    }

    // ── Sort theo cột (kể cả cột join) ────────────────────────────────────────

    /** Field thuộc chính Item. */
    private static final java.util.Set<String> SORT_PLAIN = java.util.Set.of(
            "code", "name", "unit", "unitCost", "splittable",
            "minStockQuantity", "restockQuantity", "approvalStatus", "status", "createdAt");

    /** Field join/tính toán — xử lý riêng trong {@link #sortExpression}. */
    private static final java.util.Set<String> SORT_JOINED = java.util.Set.of(
            "itemGroup.name", "defaultSupplier.name",
            "defaultPackaging.name", "defaultPackaging.qtyPerPack", "lastPrice", "sellingPrice");

    private void validateSort(Sort sort) {
        for (Sort.Order o : sort) {
            String f = o.getProperty();
            if (!SORT_PLAIN.contains(f) && !SORT_JOINED.contains(f)) {
                throw new IllegalArgumentException("Không hỗ trợ sort theo '" + f + "'. Hỗ trợ: "
                        + String.join(", ", new java.util.TreeSet<>(SORT_PLAIN))
                        + ", " + String.join(", ", new java.util.TreeSet<>(SORT_JOINED)));
            }
        }
    }

    private Specification<Item> sortSpec(Sort sort) {
        return (root, query, cb) -> {
            if (Long.class.equals(query.getResultType())) return null; // count query
            List<Order> orders = new ArrayList<>();
            for (Sort.Order o : sort) {
                Expression<?> e = sortExpression(o.getProperty(), root, query, cb);
                // null luôn cuối, bất kể asc/desc
                orders.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(e), 1).otherwise(0)));
                orders.add(o.isAscending() ? cb.asc(e) : cb.desc(e));
            }
            orders.add(cb.asc(root.get("id")));
            query.orderBy(orders);
            return null;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Expression<?> sortExpression(String field, Root<Item> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb) {
        switch (field) {
            case "itemGroup.name":
                return root.join("itemGroup", JoinType.LEFT).get("name");
            case "defaultSupplier.name": { // chỉ Ingredient có; loại khác → null
                Subquery<String> sq = query.subquery(String.class);
                Root<Ingredient> ing = sq.from(Ingredient.class);
                sq.select(ing.get("defaultSupplier").get("name"))
                        .where(cb.equal(ing.get("id"), root.get("id")));
                return sq;
            }
            case "defaultPackaging.name": {
                Subquery<String> sq = query.subquery(String.class);
                Root<ItemPackaging> pk = sq.from(ItemPackaging.class);
                sq.select(cb.least(pk.get("unit").<String>get("name")))
                        .where(cb.equal(pk.get("item").get("id"), root.get("id")),
                                cb.isTrue(pk.get("isDefault")));
                return sq;
            }
            case "defaultPackaging.qtyPerPack": {
                Subquery<BigDecimal> sq = query.subquery(BigDecimal.class);
                Root<ItemPackaging> pk = sq.from(ItemPackaging.class);
                sq.select(cb.min(pk.<BigDecimal>get("qtyPerPack")))
                        .where(cb.equal(pk.get("item").get("id"), root.get("id")),
                                cb.isTrue(pk.get("isDefault")));
                return sq;
            }
            case "sellingPrice": { // 21: giá bán nhỏ nhất trong ProductMapping; loại khác → null
                Subquery<BigDecimal> sq = query.subquery(BigDecimal.class);
                Root<ProductMapping> pm = sq.from(ProductMapping.class);
                sq.select(cb.min(pm.<BigDecimal>get("sellingPrice")))
                        .where(cb.equal(pm.get("item").get("id"), root.get("id")));
                return sq;
            }
            case "lastPrice": { // giá của dòng có effectiveDate mới nhất
                Subquery<BigDecimal> sq = query.subquery(BigDecimal.class);
                Root<IngredientPrice> ip = sq.from(IngredientPrice.class);
                Subquery<LocalDate> latest = sq.subquery(LocalDate.class);
                Root<IngredientPrice> ip2 = latest.from(IngredientPrice.class);
                latest.select(cb.greatest(ip2.<LocalDate>get("effectiveDate")))
                        .where(cb.equal(ip2.get("item").get("id"), root.get("id")));
                sq.select(cb.max(ip.<BigDecimal>get("price")))
                        .where(cb.equal(ip.get("item").get("id"), root.get("id")),
                                cb.equal(ip.get("effectiveDate"), latest));
                return sq;
            }
            default:
                return root.get(field);
        }
    }

    // ── 21: lọc hạn sử dụng / có công thức ────────────────────────────────────

    private static Integer parseIntParam(String name, String v) {
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Tham số " + name + " phải là số nguyên: '" + v + "'");
        }
    }

    /** shelfDaysMin/Max (gồm hai đầu, vắng một đầu được); chỉ áp khi itemType=PRODUCT; chưa có hạn → bị loại. */
    private Specification<Item> shelfDaysSpec(String itemType, String min, String max) {
        boolean hasMin = min != null && !min.isBlank();
        boolean hasMax = max != null && !max.isBlank();
        Integer lo = hasMin ? parseIntParam("shelfDaysMin", min) : null;
        Integer hi = hasMax ? parseIntParam("shelfDaysMax", max) : null;
        if ((lo == null && hi == null) || itemType == null || itemType.isBlank() || resolveClass(itemType) != Product.class) {
            return (root, query, cb) -> null;
        }
        return (root, query, cb) -> {
            Subquery<Integer> sq = query.subquery(Integer.class);
            Root<ProductExpiryConfig> c = sq.from(ProductExpiryConfig.class);
            List<jakarta.persistence.criteria.Predicate> w = new ArrayList<>();
            w.add(cb.equal(c.get("item").get("id"), root.get("id")));
            if (lo != null) w.add(cb.greaterThanOrEqualTo(c.<Integer>get("shelfDays"), lo));
            if (hi != null) w.add(cb.lessThanOrEqualTo(c.<Integer>get("shelfDays"), hi));
            sq.select(cb.literal(1)).where(w.toArray(new jakarta.persistence.criteria.Predicate[0]));
            return cb.exists(sq);
        };
    }

    /**
     * hasRecipe=true|false: có công thức gắn (kể cả chưa kích hoạt) hay chưa. Chỉ áp khi itemType=PRODUCT|SEMI_PRODUCT
     * và user có RECIPES:VIEW (không thì bỏ qua để không lộ sự tồn tại của công thức qua filter).
     */
    private Specification<Item> hasRecipeSpec(String itemType, String hasRecipe) {
        if (hasRecipe == null || hasRecipe.isBlank()) return (root, query, cb) -> null;
        String v = hasRecipe.trim().toLowerCase();
        if (!v.equals("true") && !v.equals("false")) {
            throw new IllegalArgumentException("Tham số hasRecipe phải là true hoặc false: '" + hasRecipe + "'");
        }
        if (itemType == null || itemType.isBlank() || resolveClass(itemType) == Ingredient.class || !scope.canViewRecipes()) {
            return (root, query, cb) -> null;
        }
        boolean want = v.equals("true");
        boolean semi = resolveClass(itemType) == SemiProduct.class;
        return (root, query, cb) -> {
            Subquery<Integer> sq = query.subquery(Integer.class);
            Root<com.bakery.api.recipe.entity.Recipe> r = sq.from(com.bakery.api.recipe.entity.Recipe.class);
            sq.select(cb.literal(1)).where(cb.equal(r.get(semi ? "semiProduct" : "product").get("id"), root.get("id")));
            return want ? cb.exists(sq) : cb.not(cb.exists(sq));
        };
    }

    // ── Override create/update — inject ThreadLocal ───────────────────────────

    @Override
    @Transactional
    public ItemResponse create(ItemRequest req) {
        currentRequest.set(req);
        try {
            return super.create(req);
        } finally {
            currentRequest.remove();
        }
    }

    @Override
    @Transactional
    public ItemResponse update(UUID id, ItemRequest req) {
        currentRequest.set(req);
        try {
            return super.update(id, req);
        } finally {
            currentRequest.remove();
        }
    }

    // ── toEntity ──────────────────────────────────────────────────────────────

    @Override
    protected Item toEntity(ItemRequest req) {
        return switch (req.itemType().toUpperCase()) {
            case "INGREDIENT" -> {
                Ingredient e = new Ingredient();
                applyCommonFields(e, req);
                applySupplier(e, req);
                yield e;
            }
            case "SEMI_PRODUCT" -> {
                SemiProduct e = new SemiProduct();
                applyCommonFields(e, req);
                yield e;
            }
            case "PRODUCT" -> {
                Product e = new Product();
                applyCommonFields(e, req);
                yield e;
            }
            default -> throw new IllegalArgumentException("Unknown itemType: " + req.itemType());
        };
    }

    // ── applyUpdate ───────────────────────────────────────────────────────────

    @Override
    protected void applyUpdate(Item e, ItemRequest req) {
        applyCommonFields(e, req);
        if (e instanceof Ingredient ing) {
            applySupplier(ing, req);
        }
        // SemiProduct/Product: chỉ có common fields, không cần xử lý thêm
    }

    // ── toResponse ────────────────────────────────────────────────────────────

    @Override
    protected ItemResponse toResponse(Item item) {
        ItemResponse r = new ItemResponse();
        r.applyFrom(item);
        r.setCode(item.getCode());
        r.setName(item.getName());
        r.setUnit(item.getUnit());
        r.setSplittable(item.isSplittable());
        r.setGiftWithCake(item.isGiftWithCake());
        boolean withRecipe = scope.canViewRecipes();   // 17/D1: không có quyền → recipe = null, không query
        // 11/D5: giá vốn chỉ Admin
        if (scope.canSeeCost()) {
            r.setUnitCost(item.getUnitCost());
        } else if (item.getUnitCost() != null) {
            r.setPriceHidden(true);
        }
        r.setImageUrl(item.getImageUrl());
        r.setMinStockQuantity(item.getMinStockQuantity());
        r.setRestockQuantity(item.getRestockQuantity());
        if (item.getItemGroup() != null) {
            r.setItemGroup(new ReferenceValue(
                    item.getItemGroup().getCode(), item.getItemGroup().getName()));
        }
        if (item.getDefaultWarehouse() != null) {
            r.setDefaultWarehouse(new ReferenceValue(
                    item.getDefaultWarehouse().getCode(), item.getDefaultWarehouse().getName()));
        }

        if (item instanceof Ingredient ing) {
            r.setItemType("INGREDIENT");
            if (ing.getDefaultSupplier() != null) {
                r.setDefaultSupplier(new ReferenceValue(
                        ing.getDefaultSupplier().getCode(),
                        ing.getDefaultSupplier().getName()));
            }
            // Dùng pre-fetched price map nếu có (list view), không thì query riêng (detail view)
            Map<UUID, IngredientPrice> priceMap = priceMapLocal.get();
            Optional<IngredientPrice> latestPrice = priceMap != null
                    ? Optional.ofNullable(priceMap.get(ing.getId()))
                    : ingredientPriceRepository.findByItemIdOrderByEffectiveDateDesc(ing.getId())
                            .stream().findFirst();
            latestPrice.ifPresent(p -> {
                        // 11/D3: giá nhập gần nhất chỉ hiện với NCC mà user được xem giá
                        if (scope.canSeePurchasePrice(p.getSupplier() != null ? p.getSupplier().getId() : null)) {
                            r.setLastPrice(p.getPrice());
                            r.setLastPriceDate(p.getEffectiveDate());
                        } else {
                            r.setPriceHidden(true);
                        }
                    });
            // Packagings — luôn query riêng (không batch vì thường chỉ vài dòng/item)
            List<ItemPackaging> packRows = packagingRepository.findByItemIdOrderByQtyPerPackAsc(ing.getId());
            Set<UUID> inUse = packagingInUse(packRows.stream().map(ItemPackaging::getId).toList());
            List<ItemResponse.PackagingResponse> packagings = packRows.stream()
                    .map(p -> toPackagingResponse(p, inUse))
                    .toList();
            r.setPackagings(packagings);

        } else if (item instanceof SemiProduct) {
            r.setItemType("SEMI_PRODUCT");
            if (withRecipe) recipeRepository.findBySemiProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstBySemiProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));

        } else if (item instanceof Product prod) {
            r.setItemType("PRODUCT");
            expiryConfigRepository.findByItemId(item.getId())
                    .ifPresent(cfg -> r.setShelfDays(cfg.getShelfDays()));
            if (withRecipe) recipeRepository.findByProductIdAndActiveTrue(item.getId())
                    .or(() -> recipeRepository.findFirstByProductIdOrderByVersionDesc(item.getId()))
                    .ifPresent(recipe -> r.setRecipe(recipeService.mapToResponse(recipe)));
            // Giá bán — lấy từ ProductMapping đầu tiên tìm thấy
            productMappingRepository.findByItemId(item.getId())
                    .stream()
                    .filter(m -> m.getSellingPrice() != null)
                    .findFirst()
                    .ifPresent(m -> r.setSellingPrice(m.getSellingPrice()));
        }

        return r;
    }

    // ── Lifecycle hooks — Recipe bundling ─────────────────────────────────────

    /** Tạo Recipe (nếu có lines) cùng lúc với Item. Recipe inherits approvalStatus của Item.
     * Đồng thời upsert ProductExpiryConfig nếu shelfDays được set. */
    @Override
    protected void afterCreate(Item item) {
        ItemRequest req = currentRequest.get();
        if (req == null) return;

        if (item instanceof Product prod && req.shelfDays() != null) {
            upsertExpiryConfig(prod, req.shelfDays());
        }

        if (!hasRecipeLines(req)) return;

        if (item instanceof Product prod) {
            int version = recipeRepository.maxVersionByProduct(prod.getId()) + 1;
            Recipe recipe = buildProductRecipe(prod, req, version);
            recipe.setApprovalStatus(prod.getApprovalStatus());
            recipeRepository.save(recipe);
        } else if (item instanceof SemiProduct sp) {
            int version = recipeRepository.maxVersionBySemiProduct(sp.getId()) + 1;
            Recipe recipe = buildSemiProductRecipe(sp, req, version);
            recipe.setApprovalStatus(sp.getApprovalStatus());
            recipeRepository.save(recipe);
        }
    }

    /**
     * Upsert Recipe khi Item được cập nhật:
     * - Có bản PENDING_APPROVAL → replace lines
     * - Không có → tạo version mới PENDING_APPROVAL
     * Đồng thời upsert ProductExpiryConfig nếu shelfDays được set.
     */
    @Override
    protected void afterUpdate(Item item) {
        ItemRequest req = currentRequest.get();
        if (req == null) return;

        if (item instanceof Product prod && req.shelfDays() != null) {
            upsertExpiryConfig(prod, req.shelfDays());
        }

        if (!hasRecipeLines(req)) return;

        if (item instanceof Product prod) {
            recipeRepository
                    .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                            prod.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresentOrElse(
                            recipe -> replaceRecipeLines(recipe, req),
                            () -> {
                                int v = recipeRepository.maxVersionByProduct(prod.getId()) + 1;
                                Recipe r = buildProductRecipe(prod, req, v);
                                r.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
                                recipeRepository.save(r);
                            });
        } else if (item instanceof SemiProduct sp) {
            recipeRepository
                    .findFirstBySemiProductIdAndApprovalStatusOrderByVersionDesc(
                            sp.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresentOrElse(
                            recipe -> replaceRecipeLines(recipe, req),
                            () -> {
                                int v = recipeRepository.maxVersionBySemiProduct(sp.getId()) + 1;
                                Recipe r = buildSemiProductRecipe(sp, req, v);
                                r.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
                                recipeRepository.save(r);
                            });
        }
    }

    /**
     * Khi Item được APPROVE → tự động APPROVE + ACTIVATE recipe PENDING mới nhất,
     * sau đó tính lại unit_cost trong transaction REQUIRES_NEW riêng biệt.
     *
     * <p>Cost recalculation chạy qua {@link ItemCostHelper} với REQUIRES_NEW để
     * tránh "rollback-only trap": nếu RecipeCostService ném exception, chỉ
     * transaction phụ bị rollback, approve() vẫn commit thành công.
     */
    @Override
    protected void afterApprove(Item item) {
        if (item instanceof Product prod) {
            recipeRepository
                    .findFirstByProductIdAndApprovalStatusOrderByVersionDesc(
                            prod.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresent(recipe -> {
                        deactivateCurrentRecipeByProduct(prod.getId());
                        activateRecipe(recipe);
                    });
            // Tính cost trong REQUIRES_NEW riêng (không write → không deadlock với outer tx lock)
            // Sau đó set trực tiếp lên managed entity — outer tx sẽ flush khi commit
            itemCostHelper.calculateCost(item.getId()).ifPresent(cost -> {
                prod.setUnitCost(cost);
                log.debug("Cập nhật unit_cost={} cho Product {} (trong outer tx)", cost, prod.getId());
            });
        } else if (item instanceof SemiProduct sp) {
            recipeRepository
                    .findFirstBySemiProductIdAndApprovalStatusOrderByVersionDesc(
                            sp.getId(), ApprovalStatus.PENDING_APPROVAL)
                    .ifPresent(recipe -> {
                        deactivateCurrentRecipeBySemiProduct(sp.getId());
                        activateRecipe(recipe);
                    });
            itemCostHelper.calculateCost(item.getId()).ifPresent(cost -> {
                sp.setUnitCost(cost);
                log.debug("Cập nhật unit_cost={} cho SemiProduct {} (trong outer tx)", cost, sp.getId());
            });
        } else if (item instanceof Ingredient ing && ing.getUnitCost() != null) {
            // Khi approve ingredient có unitCost → tự động tạo IngredientPrice catalog entry.
            // RecipeCostService đọc ingredient_price (không đọc item.unit_cost trực tiếp),
            // nên phải sync để cost calculation dùng đúng giá mới.
            upsertIngredientPrice(ing);
        }
    }

    /** Trước khi xóa Item → deactivate tất cả recipe đi kèm. */
    @Override
    protected void beforeDelete(Item item) {
        List<Recipe> recipes = item instanceof Product prod
                ? recipeRepository.findByProductId(prod.getId())
                : item instanceof SemiProduct sp
                        ? recipeRepository.findBySemiProductId(sp.getId())
                        : List.of();
        if (!recipes.isEmpty()) {
            recipes.forEach(r -> r.setActive(false));
            recipeRepository.saveAll(recipes);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Tạo hoặc cập nhật IngredientPrice catalog cho nguyên liệu.
     * Nếu đã có entry với effective_date = hôm nay → update giá, tránh tạo duplicate.
     * Nếu chưa có → tạo mới với effective_date = today.
     *
     * <p>Được gọi tự động khi approve Ingredient có unitCost,
     * để RecipeCostService (đọc từ ingredient_price) dùng đúng giá mới.
     */
    private void upsertIngredientPrice(Ingredient ing) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.math.BigDecimal price = ing.getUnitCost().setScale(2, java.math.RoundingMode.HALF_UP);
        List<IngredientPrice> existing =
                ingredientPriceRepository.findByItemIdOrderByEffectiveDateDesc(ing.getId());

        // Nếu đã có entry hôm nay → update giá, tránh tạo duplicate cùng ngày
        existing.stream()
                .filter(p -> today.equals(p.getEffectiveDate()))
                .findFirst()
                .ifPresentOrElse(
                        p -> { p.setPrice(price); ingredientPriceRepository.save(p); },
                        () -> {
                            IngredientPrice np = new IngredientPrice();
                            np.setItem(ing);
                            np.setPrice(price);
                            np.setEffectiveDate(today);
                            np.setApprovalStatus(ApprovalStatus.APPROVED);
                            ingredientPriceRepository.save(np);
                        });
    }

    private void applyCommonFields(Item e, ItemRequest req) {
        e.setCode(req.code());
        e.setName(req.name());
        e.setUnit(req.unit());
        e.setSplittable(req.splittable());
        if (req.giftWithCake() != null) e.setGiftWithCake(req.giftWithCake());
        if (req.itemGroupId() != null) {
            ItemGroup ig = itemGroupRepository.findById(req.itemGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("ItemGroup", req.itemGroupId()));
            e.setItemGroup(ig);
        } else {
            e.setItemGroup(null);
        }
        if (req.defaultWarehouseId() != null) {
            e.setDefaultWarehouse(warehouseRepository.findById(req.defaultWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", req.defaultWarehouseId())));
        } else {
            e.setDefaultWarehouse(null);
        }
        // 20/D10: BTP bắt buộc có kho mặc định ở khu sản xuất (lô BTP làm xong vào kho này). Chỉ chặn khi tạo/sửa, không đụng dữ liệu cũ.
        if (isSemi(e) && !com.bakery.api.inventory.service.ProductionZone.contains(e.getDefaultWarehouse())) {
            throw new IllegalArgumentException("Bán thành phẩm phải có kho mặc định (Kho Bếp hoặc Phòng Lạnh)");
        }
        // unitCost: chỉ áp dụng cho INGREDIENT (nhập tay).
        // SEMI_PRODUCT / PRODUCT được tính lại trong afterApprove → không ghi đè ở đây.
        if ("INGREDIENT".equalsIgnoreCase(req.itemType())) {
            e.setUnitCost(req.unitCost());
        }
        // minStockQuantity — chỉ áp dụng cho INGREDIENT và SEMI_PRODUCT
        if (!"PRODUCT".equalsIgnoreCase(req.itemType())) {
            e.setMinStockQuantity(req.minStockQuantity());
            e.setRestockQuantity(req.restockQuantity());
        }
    }

    private void applySupplier(Ingredient e, ItemRequest req) {
        if (req.defaultSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(req.defaultSupplierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Supplier", req.defaultSupplierId()));
            e.setDefaultSupplier(supplier);
        } else {
            e.setDefaultSupplier(null);
        }
    }

    private Recipe buildProductRecipe(Product product, ItemRequest req, int version) {
        Recipe recipe = new Recipe();
        recipe.setProduct(product);
        recipe.setNote(req.recipeNote());
        recipe.setYieldQuantity(req.recipeYieldQuantity());
        recipe.setActive(false);
        recipe.setVersion(version);
        recipe.setCreatedBy(actorResolver.currentUserId());
        applyRecipeLines(recipe, req.recipeLines());
        return recipe;
    }

    private Recipe buildSemiProductRecipe(SemiProduct sp, ItemRequest req, int version) {
        Recipe recipe = new Recipe();
        recipe.setSemiProduct(sp);
        recipe.setNote(req.recipeNote());
        recipe.setYieldQuantity(req.recipeYieldQuantity());
        recipe.setActive(false);
        recipe.setVersion(version);
        recipe.setCreatedBy(actorResolver.currentUserId());
        applyRecipeLines(recipe, req.recipeLines());
        return recipe;
    }

    private void replaceRecipeLines(Recipe recipe, ItemRequest req) {
        recipe.setNote(req.recipeNote());
        recipe.setYieldQuantity(req.recipeYieldQuantity());
        // Force dirty Recipe header — nếu chỉ lines thay đổi, Hibernate không detect
        // Recipe là dirty (lines là @NotAudited) → không có UPDATE SQL → Envers bỏ sót.
        // Set updatedAt thủ công để trigger dirty check.
        recipe.setUpdatedAt(java.time.Instant.now());
        recipe.getLines().clear();
        applyRecipeLines(recipe, req.recipeLines());
        recipeRepository.save(recipe);
    }

    private void activateRecipe(Recipe recipe) {
        recipe.setApprovalStatus(ApprovalStatus.APPROVED);
        recipe.setActive(true);
        recipeRepository.save(recipe);
    }

    /**
     * Deactivate recipe đang active của 1 product — dùng entity-level save thay vì
     * bulk @Modifying @Query để Envers capture được thay đổi vào recipe_HIS.
     */
    private void deactivateCurrentRecipeByProduct(UUID productId) {
        recipeRepository.findByProductIdAndActiveTrue(productId)
                .ifPresent(r -> {
                    r.setActive(false);
                    recipeRepository.save(r);
                });
    }

    /**
     * Deactivate recipe đang active của 1 semi-product — tương tự deactivateCurrentRecipeByProduct.
     */
    private void deactivateCurrentRecipeBySemiProduct(UUID semiProductId) {
        recipeRepository.findBySemiProductIdAndActiveTrue(semiProductId)
                .ifPresent(r -> {
                    r.setActive(false);
                    recipeRepository.save(r);
                });
    }

    private void applyRecipeLines(Recipe recipe, List<RecipeLineRequest> lineRequests) {
        for (int i = 0; i < lineRequests.size(); i++) {
            RecipeLineRequest lr = lineRequests.get(i);
            Item item = repository.findById(lr.itemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", lr.itemId()));
            RecipeLine line = new RecipeLine();
            line.setRecipe(recipe);
            line.setItem(item);
            line.setQuantity(lr.quantity());
            line.setUnit(lr.unit());
            line.setSortOrder(lr.sortOrder() != null ? lr.sortOrder() : i + 1);
            recipe.getLines().add(line);
        }
    }

    /** Tạo hoặc cập nhật ProductExpiryConfig cho sản phẩm. */
    private void upsertExpiryConfig(Product product, int shelfDays) {
        ProductExpiryConfig cfg = expiryConfigRepository.findByItemId(product.getId())
                .orElseGet(() -> {
                    ProductExpiryConfig c = new ProductExpiryConfig();
                    c.setItem(product);
                    return c;
                });
        cfg.setShelfDays(shelfDays);
        expiryConfigRepository.save(cfg);
    }

    private boolean hasRecipeLines(ItemRequest req) {
        return req.recipeLines() != null && !req.recipeLines().isEmpty();
    }

    // ── Packaging management ──────────────────────────────────────────────────

    /**
     * Lưu danh sách quy cách của 1 item, cập nhật theo {@code id} (16/BE-3):
     * có id → update; không id → insert; dòng cũ không còn trong request → delete.
     * Quy cách đã dùng trong phiếu/lô ({@code inventory_request_line}, {@code stock_lot} trỏ FK tới) không xóa,
     * không đổi {@code code}/{@code qtyPerPack} được (D5); đổi {@code default} thì được.
     *
     * <p>Request không có id mà trùng {@code code} với một dòng cũ chưa được id nào nhận → coi là chính dòng đó
     * (tránh vỡ unique item_id+code với client chưa gửi id).
     *
     * <p>Rule: đúng 1 entry {@code isDefault=true} — entry đầu tiên được đánh dấu thắng; không có thì entry đầu tiên.
     */
    @Transactional
    public List<ItemResponse.PackagingResponse> upsertPackagings(
            UUID itemId, List<PackagingRequest> requests) {
        Item item = repository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));

        // 1. Kiểm request: mã đơn vị có trong danh mục, số lượng > 0, không trùng mã
        List<String> codes = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (PackagingRequest req : requests) {
            String code = req.code() == null ? "" : req.code().trim().toUpperCase();
            if (code.isEmpty()) throw new IllegalArgumentException("Đơn vị đóng gói là bắt buộc");
            if (req.qtyPerPack() == null || req.qtyPerPack().signum() <= 0) {
                throw new IllegalArgumentException("Số lượng quy đổi của " + code + " phải lớn hơn 0");
            }
            if (!seen.add(code)) throw new IllegalArgumentException("Đơn vị " + code + " bị trùng trong danh sách quy cách");
            codes.add(code);
        }
        Map<String, com.bakery.api.master.entity.Unit> units = new java.util.HashMap<>();
        unitRepository.findAllById(codes).forEach(u -> units.put(u.getCode(), u));
        for (String code : codes) {
            if (!units.containsKey(code)) throw new IllegalArgumentException("Đơn vị " + code + " không có trong danh mục");
        }

        // 2. Ghép request với dòng cũ
        List<ItemPackaging> existing = packagingRepository.findByItemIdOrderByQtyPerPackAsc(itemId);
        Map<UUID, ItemPackaging> byId = new java.util.HashMap<>();
        Map<String, ItemPackaging> byCode = new java.util.HashMap<>();
        existing.forEach(p -> { byId.put(p.getId(), p); byCode.put(p.getCode(), p); });
        Set<UUID> inUse = packagingInUse(new ArrayList<>(byId.keySet()));

        ItemPackaging[] target = new ItemPackaging[requests.size()];   // dòng cũ mà request i cập nhật; null = insert
        Set<UUID> kept = new java.util.HashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            UUID id = requests.get(i).id();
            if (id == null) continue;
            ItemPackaging p = byId.get(id);
            if (p == null) throw new IllegalArgumentException("Quy cách " + id + " không thuộc mặt hàng này");
            if (!kept.add(id)) throw new IllegalArgumentException("Quy cách " + id + " xuất hiện hai lần");
            target[i] = p;
        }
        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).id() != null) continue;
            ItemPackaging p = byCode.get(codes.get(i));
            if (p != null && kept.add(p.getId())) target[i] = p;
        }

        // 3. D5: dòng đã dùng không xóa, không đổi code / qtyPerPack
        for (ItemPackaging p : existing) {
            if (inUse.contains(p.getId()) && !kept.contains(p.getId())) throw packagingLocked(p);
        }
        for (int i = 0; i < requests.size(); i++) {
            ItemPackaging p = target[i];
            if (p != null && inUse.contains(p.getId())
                    && (!p.getCode().equals(codes.get(i)) || p.getQtyPerPack().compareTo(requests.get(i).qtyPerPack()) != 0)) {
                throw packagingLocked(p);
            }
        }

        // 4. Xóa trước (để không vướng unique item_id+code), rồi sửa, rồi thêm
        List<ItemPackaging> removed = existing.stream().filter(p -> !kept.contains(p.getId())).toList();
        if (!removed.isEmpty()) {
            packagingRepository.deleteAll(removed);
            packagingRepository.flush();
        }
        boolean hasDefault = requests.stream().anyMatch(PackagingRequest::isDefault);
        boolean defaultTaken = false;
        List<ItemPackaging> saved = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            PackagingRequest req = requests.get(i);
            ItemPackaging p = target[i] != null ? target[i] : new ItemPackaging();
            if (target[i] == null) p.setItem(item);
            p.setCode(codes.get(i));
            p.setUnit(units.get(codes.get(i)));
            p.setQtyPerPack(req.qtyPerPack());
            boolean wantDefault = hasDefault ? req.isDefault() : i == 0;
            p.setDefault(wantDefault && !defaultTaken);
            defaultTaken |= p.isDefault();
            saved.add(packagingRepository.save(p));
        }
        packagingRepository.flush();

        return saved.stream().map(p -> toPackagingResponse(p, inUse)).toList();
    }

    private static IllegalStateException packagingLocked(ItemPackaging p) {
        return new IllegalStateException("Quy cách " + p.getName()
                + " đã dùng trong phiếu/lô, không xóa hoặc sửa được. Hãy thêm quy cách mới");
    }

    private static ItemResponse.PackagingResponse toPackagingResponse(ItemPackaging p, Set<UUID> inUse) {
        ItemResponse.PackagingResponse r = new ItemResponse.PackagingResponse();
        r.setId(p.getId());
        r.setCode(p.getCode());
        r.setName(p.getName());   // 16/D1: unit.name
        r.setQtyPerPack(p.getQtyPerPack());
        r.setDefault(p.isDefault());
        r.setInUse(inUse.contains(p.getId()));
        return r;
    }

    /** Id quy cách đang có phiếu hoặc lô trỏ tới (2 query theo index packaging_id). */
    private Set<UUID> packagingInUse(java.util.Collection<UUID> ids) {
        Set<UUID> out = new java.util.HashSet<>();
        if (ids.isEmpty()) return out;
        out.addAll(packagingRepository.findIdsUsedInRequestLines(ids));
        out.addAll(packagingRepository.findIdsUsedInStockLots(ids));
        return out;
    }

    /**
     * Gợi ý packaging phù hợp nhất cho số lượng cần mua.
     *
     * <p>Rule: ưu tiên packaging nhỏ nhất mà 1 pack đủ cover {@code neededQty}.
     * Nếu không có packaging nào đủ 1 pack → trả về packaging default (user tự tính số lượng).
     *
     * @param itemId    ID của ingredient
     * @param neededQty số lượng cần mua (tính theo item.unit = canonical)
     * @return packaging được suggest + số pack cần mua
     */
    @Transactional(readOnly = true)
    public SuggestPackagingResponse suggestPackaging(UUID itemId, BigDecimal neededQty) {
        List<ItemPackaging> packagings = packagingRepository
                .findByItemIdOrderByQtyPerPackAsc(itemId);
        if (packagings.isEmpty()) {
            return null;
        }

        // Tìm packaging nhỏ nhất mà qtyPerPack >= neededQty (1 pack đủ cover)
        ItemPackaging suggested = packagings.stream()
                .filter(p -> p.getQtyPerPack().compareTo(neededQty) >= 0)
                .findFirst()
                // Fallback: packaging default nếu không có pack nào đủ
                .orElseGet(() -> packagings.stream()
                        .filter(ItemPackaging::isDefault)
                        .findFirst()
                        .orElse(packagings.get(0)));

        // Số pack cần mua = ceil(neededQty / qtyPerPack)
        BigDecimal packs = neededQty
                .divide(suggested.getQtyPerPack(), 4, java.math.RoundingMode.CEILING)
                .setScale(0, java.math.RoundingMode.CEILING);

        return new SuggestPackagingResponse(
                suggested.getId(),
                suggested.getCode(),
                suggested.getName(),
                suggested.getQtyPerPack(),
                packs.intValue());
    }

    /** Request DTO cho upsert packagings — dùng nội bộ, không extend framework record. */
    public record PackagingRequest(
            java.util.UUID id,
            String code,
            String name,   // bỏ qua: tên lấy theo unit.name (16/D1)
            BigDecimal qtyPerPack,
            boolean isDefault) {}

    /** Response DTO cho suggest-packaging endpoint. */
    public record SuggestPackagingResponse(
            java.util.UUID packagingId,
            String code,
            String name,
            BigDecimal qtyPerPack,
            int suggestedPacks) {}

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Xuất toàn bộ INGREDIENT ra file Excel (.xlsx).
     * Cột: STT, Mã, Tên, Đơn vị, Unit Size, Base Unit, Nhóm, NCC mặc định,
     *       Giá vốn, Giá nhập mới nhất, Ngày cập nhật giá.
     */
    @Transactional(readOnly = true)
    public byte[] exportIngredients() {
        // Query tất cả ingredient — dùng Specification để tận dụng ItemLookupRepository
        List<Item> ingredients = repository.findAll(
                (root, query, cb) -> cb.and(
                        cb.equal(root.type(), Ingredient.class),
                        cb.equal(root.get("status"), EntityStatus.ACTIVE)));

        // Batch-fetch latest prices
        List<UUID> ids = ingredients.stream().map(Item::getId).toList();
        Map<UUID, IngredientPrice> priceMap = ids.isEmpty()
                ? Map.of()
                : ingredientPriceRepository.findLatestByItemIds(ids)
                        .stream()
                        .collect(Collectors.toMap(p -> p.getItem().getId(), p -> p));

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Nguyên Liệu");

            // ── Header style ──────────────────────────────────────────────────
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            applyBorderAll(headerStyle);

            // ── Data style ────────────────────────────────────────────────────
            CellStyle dataStyle = wb.createCellStyle();
            applyBorderAll(dataStyle);

            CellStyle numStyle = wb.createCellStyle();
            applyBorderAll(numStyle);
            numStyle.setAlignment(HorizontalAlignment.RIGHT);
            org.apache.poi.ss.usermodel.DataFormat fmt = wb.createDataFormat();
            numStyle.setDataFormat(fmt.getFormat("#,##0.####"));

            CellStyle centerStyle = wb.createCellStyle();
            applyBorderAll(centerStyle);
            centerStyle.setAlignment(HorizontalAlignment.CENTER);

            // ── Header row ────────────────────────────────────────────────────
            String[] headers = {
                "STT", "Mã", "Tên", "Đơn vị", "Unit Size", "Base Unit",
                "Nhóm", "NCC mặc định", "Giá vốn", "Giá nhập mới nhất", "Ngày cập nhật giá"
            };
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // ── Data rows ─────────────────────────────────────────────────────
            int rowNum = 1;
            for (Item item : ingredients) {
                Ingredient ing = (Ingredient) item;
                IngredientPrice latestPrice = priceMap.get(ing.getId());

                Row row = sheet.createRow(rowNum);
                setCell(row, 0, rowNum, centerStyle);                                             // STT
                setCell(row, 1, ing.getCode(), dataStyle);                                        // Mã
                setCell(row, 2, ing.getName(), dataStyle);                                        // Tên
                setCell(row, 3, ing.getUnit(), centerStyle);                                      // Đơn vị
//                setNumCell(row, 4, ing.getUnitSize(), numStyle);                                  // Unit Size
//                setCell(row, 5, ing.getBaseUnit(), centerStyle);                                  // Base Unit
                setCell(row, 6, ing.getItemGroup() != null ? ing.getItemGroup().getName() : "", dataStyle); // Nhóm
                setCell(row, 7, ing.getDefaultSupplier() != null ? ing.getDefaultSupplier().getName() : "", dataStyle); // NCC
                setNumCell(row, 8, ing.getUnitCost(), numStyle);                                  // Giá vốn
                setNumCell(row, 9, latestPrice != null ? latestPrice.getPrice() : null, numStyle); // Giá nhập
                setCell(row, 10,                                                                   // Ngày cập nhật
                        latestPrice != null && latestPrice.getEffectiveDate() != null
                                ? latestPrice.getEffectiveDate().toString()
                                : "",
                        centerStyle);
                rowNum++;
            }

            // ── Auto-size columns ─────────────────────────────────────────────
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Thêm padding tránh bị cắt chữ
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 10000));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo file Excel", e);
        }
    }

    private static void applyBorderAll(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private static void setCell(Row row, int col, Object value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellStyle(style);
        if (value instanceof Number n) {
            c.setCellValue(n.doubleValue());
        } else if (value != null) {
            c.setCellValue(value.toString());
        }
    }

    private static void setNumCell(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellStyle(style);
        if (value != null) {
            c.setCellValue(value.doubleValue());
        }
    }

    private Class<? extends Item> resolveClass(String itemType) {
        return switch (itemType.toUpperCase()) {
            case "INGREDIENT"   -> Ingredient.class;
            case "SEMI_PRODUCT" -> SemiProduct.class;
            case "PRODUCT"      -> Product.class;
            default -> throw new IllegalArgumentException("Unknown itemType: " + itemType);
        };
    }
    /**
     * Lưu unit_cost tính từ công thức vào item — dùng bởi RecipeController /cost/apply.
     */
    @Transactional
    public void saveUnitCost(UUID itemId, BigDecimal cost) {
        Item item = repository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", itemId));
        item.setUnitCost(cost);
        log.info("saveUnitCost: item={}, cost={}", item.getCode(), cost);
    }


    /**
     * Xóa hàng loạt items (soft-delete).
     * @return số lượng item đã xóa thành công
     */
    @Transactional
    public int bulkDelete(List<java.util.UUID> ids) {
        int count = 0;
        for (java.util.UUID id : ids) {
            try { delete(id); count++; } catch (Exception ignored) {}
        }
        return count;
    }

}
