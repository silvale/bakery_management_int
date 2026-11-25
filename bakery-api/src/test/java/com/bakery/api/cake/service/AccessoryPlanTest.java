package com.bakery.api.cake.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.bakery.api.cake.dto.DesignQtyRequest;
import com.bakery.api.cake.dto.DesignQtyRequest.AccessoryQty;
import com.bakery.api.cake.entity.CakeDesign;
import com.bakery.api.cake.entity.CakeDesignAccessory;
import com.bakery.api.cake.repository.CakeDesignRepository;
import com.bakery.api.master.entity.Ingredient;
import com.bakery.api.master.entity.Item;
import com.bakery.api.master.repository.ItemLookupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 02/BE-13: kiểm và dựng danh sách phụ kiện bếp dùng (chưa chạm kho). */
class AccessoryPlanTest {

    // Mockito không mock được trên JDK 25 của dự án → dùng java.lang.reflect.Proxy cho hai repository
    private List<UUID> productIds = List.of();
    private final UUID designId = UUID.randomUUID();
    private final Item hinh = item("HINH"), nen = item("NEN"), no = item("NO");
    private CakeDesign design;

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type, java.util.function.BiFunction<String, Object[], Object> handler) {
        return (T) java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, m, args) -> handler.apply(m.getName(), args));
    }

    private final CakeDesignRepository designs = stub(CakeDesignRepository.class,
            (name, args) -> "findById".equals(name) && designId.equals(args[0]) ? Optional.of(design) : Optional.empty());
    private final ItemLookupRepository items = stub(ItemLookupRepository.class, (name, args) -> switch (name) {
        case "findById" -> no.getId().equals(args[0]) ? Optional.of(no) : Optional.empty();
        case "findProductIdsIn" -> productIds;
        default -> null;
    });
    private final DeliveryDesignService service =
            new DeliveryDesignService(designs, null, null, items, null, null, null);

    private static Item item(String code) {
        Ingredient i = new Ingredient();
        i.setId(UUID.randomUUID());
        i.setCode(code);
        i.setName(code);
        return i;
    }

    private static CakeDesignAccessory acc(Item item, String qty) {
        CakeDesignAccessory a = new CakeDesignAccessory();
        a.setItem(item);
        a.setQty(new BigDecimal(qty));
        return a;
    }

    @BeforeEach
    void setUp() {
        design = new CakeDesign();
        design.setId(designId);
        design.setCode("SN16");
        design.getAccessories().add(acc(hinh, "1"));
        design.getAccessories().add(acc(nen, "2"));
    }

    private static DesignQtyRequest req(UUID id, String qty, List<AccessoryQty> acc) {
        return new DesignQtyRequest(id, new BigDecimal(qty), acc);
    }

    @Test
    void notSentUsesDesignSetTimesQty() {
        var plan = service.planAccessories(Map.of(designId, new BigDecimal("2")), List.of(req(designId, "2", null)));
        assertThat(plan).hasSize(2);
        assertThat(plan).allSatisfy(p -> assertThat(p.qtyUsed()).isEqualByComparingTo(p.qtyDefault()));
        assertThat(plan.stream().filter(p -> p.item() == nen).findFirst().orElseThrow().qtyUsed()).isEqualByComparingTo("4");
    }

    @Test
    void emptyListMeansNoAccessories() {
        assertThat(service.planAccessories(Map.of(designId, BigDecimal.ONE), List.of(req(designId, "1", List.of())))).isEmpty();
    }

    @Test
    void editedListKeepsDefaultForComparisonAndAllowsExtraItemAndFractions() {
        var plan = service.planAccessories(Map.of(designId, new BigDecimal("2")),
                List.of(req(designId, "2", List.of(new AccessoryQty(hinh.getId(), new BigDecimal("1")),
                        new AccessoryQty(no.getId(), new BigDecimal("0.5"))))));
        assertThat(plan).hasSize(2);   // nến bị xóa → không có dòng
        var h = plan.stream().filter(p -> p.item() == hinh).findFirst().orElseThrow();
        var n = plan.stream().filter(p -> p.item() == no).findFirst().orElseThrow();
        assertThat(h.qtyDefault()).isEqualByComparingTo("2");
        assertThat(h.qtyUsed()).isEqualByComparingTo("1");
        assertThat(n.qtyDefault()).isEqualByComparingTo("0");   // thêm ngoài bộ
        assertThat(n.qtyUsed()).isEqualByComparingTo("0.5");
    }

    @Test
    void rejectsZeroQtyDuplicateAndProduct() {
        var produced = Map.of(designId, BigDecimal.ONE);
        assertThatThrownBy(() -> service.planAccessories(produced,
                List.of(req(designId, "1", List.of(new AccessoryQty(hinh.getId(), BigDecimal.ZERO))))))
                .hasMessageContaining("SN16").hasMessageContaining("lớn hơn 0");
        assertThatThrownBy(() -> service.planAccessories(produced,
                List.of(req(designId, "1", List.of(new AccessoryQty(hinh.getId(), BigDecimal.ONE), new AccessoryQty(hinh.getId(), BigDecimal.ONE))))))
                .hasMessageContaining("bị trùng");
        productIds = List.of(no.getId());
        assertThatThrownBy(() -> service.planAccessories(produced,
                List.of(req(designId, "1", List.of(new AccessoryQty(no.getId(), BigDecimal.ONE))))))
                .hasMessageContaining("thành phẩm");
    }
}
