package com.bakery.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.bakery.api.auth.dto.AuthProfile;
import com.bakery.api.auth.dto.LoginResponse;
import com.bakery.api.auth.dto.UserRoleResponse;
import com.bakery.api.inventory.dto.InventoryRequestLineResponse;
import com.bakery.api.inventory.dto.InventoryRequestResponse;
import com.bakery.api.inventory.dto.StockLotResponse;
import com.bakery.api.master.dto.ItemResponse;
import com.bakery.api.master.dto.SupplierRequest;
import com.bakery.api.master.dto.SupplierResponse;
import com.bakery.framework.metadata.ReferenceValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Contract JSON của 11 (§7): tên field boolean phải đúng từng chữ, và KHÔNG xuất hiện dạng thứ hai
 * (vd {@code cashPurchase}, {@code system}, {@code admin}). Dùng ObjectMapper cấu hình như Spring Boot.
 */
class JsonContractTest {

    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    private JsonNode json(Object o) { return mapper.valueToTree(o); }

    @Test
    void supplierUsesIsCashPurchase() throws Exception {
        SupplierResponse r = new SupplierResponse();
        r.setCashPurchase(true);
        JsonNode n = json(r);
        assertThat(n.get("isCashPurchase").asBoolean()).isTrue();
        assertThat(n.has("cashPurchase")).isFalse();

        SupplierRequest req = mapper.readValue(
                "{\"code\":\"C\",\"name\":\"Chợ\",\"isCashPurchase\":true}", SupplierRequest.class);
        assertThat(req.isCashPurchase()).isTrue();
        assertThat(mapper.readValue("{\"code\":\"C\",\"name\":\"x\"}", SupplierRequest.class).isCashPurchase()).isNull();   // không gửi = giữ nguyên
    }

    @Test
    void roleUsesIsSystem() {
        UserRoleResponse r = new UserRoleResponse();
        r.setSystem(true);
        JsonNode n = json(r);
        assertThat(n.get("isSystem").asBoolean()).isTrue();
        assertThat(n.has("system")).isFalse();
    }

    @Test
    void authResponsesUseIsAdmin() {
        AuthProfile p = new AuthProfile(UUID.randomUUID(), "u", "U", new ReferenceValue("MAIN_STAFF", "NV"), true,
                List.of(), List.of(), Map.of());
        assertThat(json(p).get("isAdmin").asBoolean()).isTrue();
        assertThat(json(p).has("admin")).isFalse();
        JsonNode login = json(LoginResponse.of(UUID.randomUUID(), "u", "U", null, "R", "N", "a", "r", p));
        assertThat(login.get("isAdmin").asBoolean()).isTrue();
        assertThat(login.has("admin")).isFalse();
        assertThat(login.has("permissionMap")).isTrue();
    }

    @Test
    void priceAndEditFlagsKeepTheirNames() {
        InventoryRequestResponse r = new InventoryRequestResponse();
        r.setPriceHidden(true);
        r.setPriceMissing(true);
        r.setCreatedByMe(true);
        r.setCanEdit(true);
        r.setCanUnpay(true);
        JsonNode n = json(r);
        for (String f : List.of("priceHidden", "priceMissing", "createdByMe", "canEdit", "canUnpay")) {
            assertThat(n.get(f).asBoolean()).as(f).isTrue();
        }
        InventoryRequestLineResponse l = new InventoryRequestLineResponse();
        l.setPriceHidden(true);
        l.setUnitCost(null);
        assertThat(json(l).get("priceHidden").asBoolean()).isTrue();
        assertThat(json(l).get("unitCost").isNull()).isTrue();

        ItemResponse i = new ItemResponse();
        i.setPriceHidden(true);
        assertThat(json(i).get("priceHidden").asBoolean()).isTrue();
        StockLotResponse s = new StockLotResponse();
        s.setPriceHidden(true);
        s.setUnitCost(BigDecimal.ZERO);
        assertThat(json(s).get("priceHidden").asBoolean()).isTrue();
    }
}
