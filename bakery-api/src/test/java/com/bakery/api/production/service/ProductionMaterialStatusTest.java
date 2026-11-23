package com.bakery.api.production.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.bakery.api.production.entity.ProductionRequest;
import com.bakery.api.production.entity.ProductionRequestLine;
import com.bakery.api.production.repository.ProductionMaterialRepository;
import com.bakery.framework.entity.ProductionLineStatus;
import com.bakery.framework.entity.ProductionType;
import org.junit.jupiter.api.Test;

/** 20/BE-3: materialStatus và chặn hủy khi còn dư. Repository dựng bằng Proxy (Mockito không chạy trên JDK 25). */
class ProductionMaterialStatusTest {

    private static ProductionMaterialService service(long rows, String remaining) {
        ProductionMaterialRepository repo = (ProductionMaterialRepository) java.lang.reflect.Proxy.newProxyInstance(
                ProductionMaterialRepository.class.getClassLoader(), new Class<?>[]{ProductionMaterialRepository.class},
                (p, m, a) -> {
                    if (m.getName().equals("summary")) {
                        return List.<Object[]>of(new Object[]{rows, new BigDecimal(remaining)});
                    }
                    throw new UnsupportedOperationException(m.getName());
                });
        return new ProductionMaterialService(null, repo, null, null, null, null, null, null, null);
    }

    private static ProductionRequest request(ProductionType type, ProductionLineStatus... lines) {
        ProductionRequest r = new ProductionRequest();
        r.setProductionType(type);
        for (ProductionLineStatus st : lines) {
            ProductionRequestLine l = new ProductionRequestLine();
            l.setLineStatus(st);
            r.getLines().add(l);
        }
        return r;
    }

    private static final ProductionLineStatus DONE = ProductionLineStatus.COMPLETED;
    private static final ProductionLineStatus OPEN = ProductionLineStatus.PENDING;

    @Test
    void statusFollowsLinesAndTempTable() {
        assertThat(service(0, "0").statusOf(request(ProductionType.SEMI, OPEN))).isEqualTo("NONE");
        assertThat(service(2, "1.5").statusOf(request(ProductionType.SEMI, OPEN, DONE))).isEqualTo("IN_PROGRESS");
        assertThat(service(2, "0.2").statusOf(request(ProductionType.SEMI, DONE, DONE))).isEqualTo("TO_SETTLE");
        assertThat(service(2, "0").statusOf(request(ProductionType.SEMI, DONE, DONE))).isEqualTo("READY_TO_CLOSE");
        ProductionRequest closed = request(ProductionType.SEMI, DONE);
        closed.setClosedAt(Instant.now());
        assertThat(service(2, "0").statusOf(closed)).isEqualTo("CLOSED");
    }

    @Test
    void nonSemiRequestHasNoMaterialStatus() {
        assertThat(service(0, "0").statusOf(request(ProductionType.DAILY, OPEN))).isNull();
    }

    @Test
    void cannotCancelWhileLeftoverInTempTable() {
        ProductionRequest semi = request(ProductionType.SEMI, DONE);
        semi.setId(java.util.UUID.randomUUID());
        assertThatThrownBy(() -> service(1, "0.2").requireSettled(semi)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Còn nguyên liệu đang sản xuất");
        service(1, "0").requireSettled(semi);                                          // đã đối soát hết → qua
        service(1, "9").requireSettled(request(ProductionType.DAILY, DONE));           // DAILY không áp dụng
    }
}
