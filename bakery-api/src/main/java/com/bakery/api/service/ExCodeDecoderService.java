package com.bakery.api.service;

import com.bakery.common.entity.Product;
import com.bakery.common.entity.ProductExpiryConfig;
import com.bakery.common.entity.ProductPrefix;
import com.bakery.common.repository.ProductExpiryConfigRepository;
import com.bakery.common.repository.ProductPrefixRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Giải mã EX_CODE từ máy POS → IN_CODE + ngày sản xuất + ngày hết hạn.
 *
 * Cấu trúc EX_CODE:  [Prefix][ThứSX][Random]
 *   BM2546892  → prefix="BM", thứ=2(T2), random="546892"
 *   BKST5123   → prefix="BKST", thứ=5(T5), random="123"
 *
 * ThứSX mapping:
 *   2=Thứ Hai, 3=Thứ Ba, 4=Thứ Tư, 5=Thứ Năm,
 *   6=Thứ Sáu, 7=Thứ Bảy, 8=Chủ Nhật
 *
 * Thuật toán xác định tuần:
 *   Tìm ngày gần nhất (từ hôm nay trở về trước) có đúng thứ đó
 *   và nằm trong HSD của sản phẩm.
 *   Đảm bảo: KHÔNG có sản phẩm hết hạn được bán ra.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExCodeDecoderService {

    private final ProductPrefixRepository     productPrefixRepository;
    private final ProductExpiryConfigRepository expiryConfigRepository;

    // ── Public API ────────────────────────────────────────────

    /**
     * Decode EX_CODE → DecodeResult.
     *
     * @param exCode   EX_CODE từ máy POS (VD: BM2546892)
     * @param today    Ngày đang xử lý (thường = ngày chạy batch)
     * @return DecodeResult, hoặc null nếu không decode được
     */
    public DecodeResult decode(String exCode, LocalDate today) {
        if (exCode == null || exCode.isBlank()) return null;

        // 1. Longest-match prefix
        List<ProductPrefix> prefixes = productPrefixRepository
            .findAllActiveOrderByPrefixLengthDesc();

        ProductPrefix matched = null;
        for (ProductPrefix pp : prefixes) {
            if (exCode.toUpperCase().startsWith(pp.getPrefix().toUpperCase())) {
                matched = pp;
                break;
            }
        }

        if (matched == null) {
            log.warn("EX_CODE không match prefix nào: {}", exCode);
            return null;
        }

        // 2. Lấy ký tự ngay sau prefix = thứ sản xuất
        String remainder = exCode.substring(matched.getPrefix().length());
        if (remainder.isEmpty()) {
            log.warn("EX_CODE thiếu thứ sản xuất: {}", exCode);
            return null;
        }

        char dayChar = remainder.charAt(0);

        // dayChar hợp lệ: '0' = sản xuất trong ngày (HSD trong ngày), '2'..'8' = thứ SX
        if (dayChar != '0' && (dayChar < '2' || dayChar > '8')) {
            log.warn("EX_CODE thứ sản xuất không hợp lệ '{}': {}", dayChar, exCode);
            return null;
        }

        int dayCode = Character.getNumericValue(dayChar); // 0 hoặc 2..8

        // 3. Tra cứu HSD sản phẩm
        Product product = matched.getProduct();
        int shelfDays = expiryConfigRepository.findByProductId(product.getId())
            .map(ProductExpiryConfig::getShelfDays)
            .orElse(4); // mặc định 4 ngày nếu chưa cấu hình

        // 4. Xác định ngày SX
        //    dayCode = 0 → HSD trong ngày → productionDate = today
        //    dayCode = 2..8 → tìm ngày SX gần nhất theo thứ trong tuần
        LocalDate productionDate;
        if (dayCode == 0) {
            productionDate = today; // Sản xuất trong ngày, không cần resolve
        } else {
            productionDate = resolveProductionDate(dayCode, shelfDays, today);
        }

        if (productionDate == null) {
            log.warn("Không tìm được ngày SX hợp lệ cho EX_CODE: {} (thứ={}, HSD={}n)",
                exCode, dayCode, shelfDays);
            return null;
        }

        LocalDate expiryDate = productionDate.plusDays(shelfDays);

        log.debug("Decode {} → IN_CODE={} | SX={} | HSD={}",
            exCode, product.getCode(), productionDate, expiryDate);

        return new DecodeResult(
            exCode,
            product,
            productionDate,
            expiryDate,
            dayCode,
            matched.getPrefix()
        );
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Tìm ngày SX gần nhất có đúng thứ dayCode và còn trong HSD.
     *
     * Quy tắc:
     *   - Tìm ngày gần nhất từ today trở về trước
     *   - Đúng thứ trong tuần (dayCode 2..8)
     *   - today - productionDate <= shelfDays (còn hạn)
     *   - Không thể là ngày tương lai
     *
     * Ví dụ hôm nay T5, thứ=7 (T7):
     *   → T7 gần nhất = 2 ngày trước → OK nếu HSD >= 2
     *   Hôm nay T2, thứ=7:
     *   → T7 gần nhất = 3 ngày trước (tuần trước) → OK nếu HSD >= 3
     *
     * @param dayCode   Thứ sản xuất (2..8)
     * @param shelfDays HSD sản phẩm (ngày)
     * @param today     Ngày tham chiếu
     * @return Ngày sản xuất hợp lệ, hoặc null nếu đã quá HSD
     */
    private LocalDate resolveProductionDate(int dayCode, int shelfDays, LocalDate today) {
        DayOfWeek targetDow = toDayOfWeek(dayCode);

        // Tìm trong khoảng shelfDays ngày gần nhất
        for (int offset = 0; offset <= shelfDays; offset++) {
            LocalDate candidate = today.minusDays(offset);
            if (candidate.getDayOfWeek() == targetDow) {
                // Kiểm tra còn hạn: today - candidate <= shelfDays
                long daysOld = today.toEpochDay() - candidate.toEpochDay();
                if (daysOld <= shelfDays) {
                    return candidate;
                } else {
                    // Đã quá HSD, không tìm tiếp
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Chuyển mã thứ (2..8) → Java DayOfWeek.
     * 2=T2=MONDAY ... 7=T7=SATURDAY, 8=CN=SUNDAY
     */
    static DayOfWeek toDayOfWeek(int dayCode) {
        return switch (dayCode) {
            case 2 -> DayOfWeek.MONDAY;
            case 3 -> DayOfWeek.TUESDAY;
            case 4 -> DayOfWeek.WEDNESDAY;
            case 5 -> DayOfWeek.THURSDAY;
            case 6 -> DayOfWeek.FRIDAY;
            case 7 -> DayOfWeek.SATURDAY;
            case 8 -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("dayCode không hợp lệ: " + dayCode);
        };
    }

    // ── Result record ─────────────────────────────────────────

    /**
     * Kết quả decode 1 EX_CODE.
     */
    public record DecodeResult(
        String    exCode,
        Product   product,         // Master product (IN_CODE)
        LocalDate productionDate,  // Ngày sản xuất đã resolve
        LocalDate expiryDate,      // Ngày hết hạn = productionDate + shelfDays
        int       dayCode,         // Thứ sản xuất (2..8)
        String    prefix           // Prefix đã match
    ) {
        public boolean isExpiredOn(LocalDate date) {
            return date.isAfter(expiryDate);
        }

        public boolean isExpiringSoon(LocalDate date, int withinDays) {
            return !isExpiredOn(date)
                && !date.plusDays(withinDays).isBefore(expiryDate);
        }
    }
}
