package com.bakery.api.modules.inventory.services;

import com.bakery.api.modules.inventory.dtos.InventoryLotResponse;
import com.bakery.api.modules.inventory.dtos.InventoryStockResponse;
import com.bakery.api.modules.masterdata.entities.Branch;
import com.bakery.api.modules.masterdata.entities.Ingredient;
import com.bakery.api.modules.inventory.entities.Inventory;
import com.bakery.api.modules.masterdata.entities.Product;
import com.bakery.api.framework.enums.ItemType;
import com.bakery.api.modules.masterdata.repositories.BranchRepository;
import com.bakery.api.modules.masterdata.repositories.IngredientRepository;
import com.bakery.api.modules.inventory.repositories.InventoryRepository;
import com.bakery.api.modules.masterdata.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private final InventoryRepository   inventoryRepo;
    private final BranchRepository      branchRepo;
    private final ProductRepository     productRepo;
    private final IngredientRepository  ingredientRepo;

    /**
     * Tổng tồn kho theo từng item tại 1 chi nhánh.
     * itemType null → tất cả (INGREDIENT + PRODUCT).
     */
    @Transactional(readOnly = true)
    public List<InventoryStockResponse> getStockByBranch(UUID branchId, ItemType itemType) {
        Branch branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch không tồn tại: " + branchId));

        List<Inventory> lots = itemType != null
                ? inventoryRepo.findAvailableByBranchAndItemType(branchId, itemType)
                : inventoryRepo.findAvailableByBranch(branchId);

        // Nhóm các lô theo itemId
        Map<UUID, List<Inventory>> byItem = lots.stream()
                .collect(Collectors.groupingBy(Inventory::getItemId));

        // Batch load item metadata
        Map<UUID, String[]> itemMeta = loadItemMeta(lots);

        return byItem.entrySet().stream()
                .map(entry -> {
                    UUID           itemId   = entry.getKey();
                    List<Inventory> itemLots = entry.getValue();
                    String[]       meta     = itemMeta.getOrDefault(itemId, new String[]{null, null});

                    java.math.BigDecimal totalQty = itemLots.stream()
                            .map(Inventory::getQtyAvailable)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

                    LocalDate earliestExpiry = itemLots.stream()
                            .map(Inventory::getExpiryDate)
                            .filter(Objects::nonNull)
                            .min(LocalDate::compareTo)
                            .orElse(null);

                    return InventoryStockResponse.builder()
                            .branchId(branchId)
                            .branchName(branch.getName())
                            .itemId(itemId)
                            .itemCode(meta[0])
                            .itemName(meta[1])
                            .itemType(itemLots.get(0).getItemType())
                            .totalQtyAvailable(totalQty)
                            .activeLotCount(itemLots.size())
                            .earliestExpiryDate(earliestExpiry)
                            .build();
                })
                .sorted(Comparator.comparing(r -> r.getItemCode() != null ? r.getItemCode() : ""))
                .toList();
    }

    /**
     * Chi tiết tất cả lô FEFO của 1 item tại 1 chi nhánh.
     * includeEmpty = true → bao gồm cả lô đã hết hàng (audit trail).
     */
    @Transactional(readOnly = true)
    public List<InventoryLotResponse> getLotsByItem(UUID branchId, UUID itemId,
                                                     ItemType itemType, boolean includeEmpty) {
        Branch branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Branch không tồn tại: " + branchId));

        List<Inventory> lots = includeEmpty
                ? inventoryRepo.findAllLotsByItem(branchId, itemId, itemType)
                : inventoryRepo.findAvailableFefo(branchId, itemId, itemType);

        String[] meta = resolveItemMeta(itemId, itemType);
        LocalDate today = LocalDate.now();

        return lots.stream().map(lot -> InventoryLotResponse.builder()
                .id(lot.getId())
                .branchId(branchId)
                .branchName(branch.getName())
                .itemId(itemId)
                .itemCode(meta[0])
                .itemName(meta[1])
                .itemType(lot.getItemType())
                .qtyAvailable(lot.getQtyAvailable())
                .lotNumber(lot.getLotNumber())
                .expiryDate(lot.getExpiryDate())
                .daysUntilExpiry(lot.getExpiryDate() != null
                        ? ChronoUnit.DAYS.between(today, lot.getExpiryDate()) : null)
                .costPerUnit(lot.getCostPerUnit())
                .sourceTxId(lot.getSourceTxId())
                .createdAt(lot.getCreatedAt())
                .build()
        ).toList();
    }

    /**
     * Cảnh báo các lô sắp hết hạn trong N ngày tới.
     * branchId null → tất cả chi nhánh.
     */
    @Transactional(readOnly = true)
    public List<InventoryLotResponse> getExpiringSoon(UUID branchId, int days) {
        LocalDate warningDate = LocalDate.now().plusDays(days);
        LocalDate today       = LocalDate.now();

        List<Inventory> lots = branchId != null
                ? inventoryRepo.findExpiringSoonByBranch(branchId, warningDate)
                : inventoryRepo.findExpiringSoon(warningDate);

        Map<UUID, String[]> itemMeta = loadItemMeta(lots);
        Map<UUID, Branch>   branches = loadBranches(lots);

        return lots.stream().map(lot -> {
            String[] meta   = itemMeta.getOrDefault(lot.getItemId(), new String[]{null, null});
            Branch   branch = branches.get(lot.getBranch().getId());
            return InventoryLotResponse.builder()
                    .id(lot.getId())
                    .branchId(lot.getBranch().getId())
                    .branchName(branch != null ? branch.getName() : null)
                    .itemId(lot.getItemId())
                    .itemCode(meta[0])
                    .itemName(meta[1])
                    .itemType(lot.getItemType())
                    .qtyAvailable(lot.getQtyAvailable())
                    .lotNumber(lot.getLotNumber())
                    .expiryDate(lot.getExpiryDate())
                    .daysUntilExpiry(lot.getExpiryDate() != null
                            ? ChronoUnit.DAYS.between(today, lot.getExpiryDate()) : null)
                    .costPerUnit(lot.getCostPerUnit())
                    .sourceTxId(lot.getSourceTxId())
                    .createdAt(lot.getCreatedAt())
                    .build();
        }).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Batch load product + ingredient metadata để tránh N+1 */
    private Map<UUID, String[]> loadItemMeta(List<Inventory> lots) {
        Set<UUID> productIds    = new HashSet<>();
        Set<UUID> ingredientIds = new HashSet<>();

        for (Inventory lot : lots) {
            if (lot.getItemType() == ItemType.PRODUCT)     productIds.add(lot.getItemId());
            else                                            ingredientIds.add(lot.getItemId());
        }

        Map<UUID, String[]> meta = new HashMap<>();

        if (!productIds.isEmpty()) {
            productRepo.findAllById(productIds).forEach(p ->
                    meta.put(p.getId(), new String[]{p.getCode(), p.getName()}));
        }
        if (!ingredientIds.isEmpty()) {
            ingredientRepo.findAllById(ingredientIds).forEach(i ->
                    meta.put(i.getId(), new String[]{i.getCode(), i.getName()}));
        }
        return meta;
    }

    private Map<UUID, Branch> loadBranches(List<Inventory> lots) {
        List<UUID> ids = lots.stream().map(i -> i.getBranch().getId()).distinct().toList();
        return branchRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(Branch::getId, Function.identity()));
    }

    private String[] resolveItemMeta(UUID itemId, ItemType itemType) {
        if (itemType == ItemType.PRODUCT) {
            return productRepo.findById(itemId)
                    .map(p -> new String[]{p.getCode(), p.getName()})
                    .orElse(new String[]{null, null});
        } else {
            return ingredientRepo.findById(itemId)
                    .map(i -> new String[]{i.getCode(), i.getName()})
                    .orElse(new String[]{null, null});
        }
    }
}
