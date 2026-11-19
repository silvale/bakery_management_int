package com.bakery.api.cake.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.bakery.api.cake.dto.DesignQtyRequest;

/** Kiểm tra danh sách "số bánh theo mẫu" gửi lên: mẫu hợp lệ, số nguyên không âm, tổng khớp. */
public final class DesignSplit {

    private DesignSplit() {}

    /**
     * @param allowed    id các mẫu được phép chọn
     * @param total      tổng số bánh (qtyProduced hoặc qtyReceived)
     * @param totalLabel "làm ra" | "thực nhận" — dùng trong thông báo lệch tổng
     * @return designId → qty (chỉ các mẫu qty &gt; 0); rỗng khi total ≤ 0
     * @throws IllegalArgumentException (→ 400) khi không hợp lệ
     */
    public static Map<UUID, BigDecimal> validate(List<DesignQtyRequest> designs, Set<UUID> allowed,
            BigDecimal total, String totalLabel) {
        return validate(designs, allowed, total, totalLabel, UUID::toString);   // không biết mã: hiện id (chỉ dùng trong test)
    }

    /** Thông báo lỗi về một mẫu: ghi mã mẫu (vd SN20); id không tồn tại thì "Không tìm thấy mẫu {id}" (02/BE-10). */
    public static String notAllowedMessage(UUID id, java.util.function.Function<UUID, String> codeOf) {
        String code = codeOf.apply(id);
        return code == null ? "Không tìm thấy mẫu " + id
                : "Mẫu " + code + " không thuộc thành phẩm của dòng này hoặc đã ngừng dùng";
    }

    /** @param codeOf id mẫu → mã mẫu, null nếu không có mẫu đó */
    public static Map<UUID, BigDecimal> validate(List<DesignQtyRequest> designs, Set<UUID> allowed,
            BigDecimal total, String totalLabel, java.util.function.Function<UUID, String> codeOf) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        if (total == null || total.signum() <= 0) return result;
        if (designs == null || designs.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn mẫu bánh");
        }
        Set<UUID> seen = new java.util.HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (DesignQtyRequest d : designs) {
            if (d == null || d.designId() == null) throw new IllegalArgumentException("Thiếu designId");
            if (!seen.add(d.designId())) throw new IllegalArgumentException("Mẫu bị chọn trùng");
            if (!allowed.contains(d.designId())) {
                throw new IllegalArgumentException(notAllowedMessage(d.designId(), codeOf));
            }
            BigDecimal q = d.qty();
            if (q == null || q.signum() < 0 || q.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("Số lượng mẫu phải là số nguyên không âm");
            }
            sum = sum.add(q);
            if (q.signum() > 0) result.put(d.designId(), q);
        }
        if (sum.compareTo(total) != 0) {
            throw new IllegalArgumentException("Tổng số mẫu (" + plain(sum) + ") khác số lượng " + totalLabel
                    + " (" + plain(total) + ")");
        }
        return result;
    }

    public static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
