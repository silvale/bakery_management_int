package com.bakery.api.master.util;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Giải mã EX_CODE từ máy POS để lấy ngày sản xuất.
 *
 * <p>Cấu trúc EX_CODE: {@code [Prefix][dayChar][Random]}
 * <ul>
 *   <li>{@code BM2546892} → prefix="BM", dayChar='2' (Thứ Hai)</li>
 *   <li>{@code BKST5123}  → prefix="BKST", dayChar='5' (Thứ Năm)</li>
 *   <li>{@code BM08A9}    → prefix="BM", dayChar='0' (sản xuất hằng ngày)</li>
 * </ul>
 *
 * <p>dayChar mapping: '0'=mỗi ngày, '2'=T2, '3'=T3, '4'=T4,
 * '5'=T5, '6'=T6, '7'=T7, '8'=CN.
 */
public final class ExCodeDecoder {

    private ExCodeDecoder() {}

    /**
     * Trích dayChar từ EX_CODE, biết trước groupCode (prefix).
     *
     * @param exCode    mã EX_CODE đầy đủ
     * @param groupCode mã nhóm item (e.g. "BMN", "BM", "BKST")
     * @return dayChar nếu hợp lệ, hoặc null nếu không decode được
     */
    public static Character extractDayChar(String exCode, String groupCode) {
        if (exCode == null || groupCode == null) return null;
        // EX_CODE phải bắt đầu bằng groupCode
        if (!exCode.startsWith(groupCode)) return null;
        int idx = groupCode.length();
        if (idx >= exCode.length()) return null;
        char c = exCode.charAt(idx);
        if (c == '0' || (c >= '2' && c <= '8')) return c;
        return null;
    }

    /**
     * Map dayChar → DayOfWeek (Java).
     * '0' → null (sản xuất hằng ngày, không ràng buộc thứ).
     */
    public static DayOfWeek toDayOfWeek(char dayChar) {
        return switch (dayChar) {
            case '2' -> DayOfWeek.MONDAY;
            case '3' -> DayOfWeek.TUESDAY;
            case '4' -> DayOfWeek.WEDNESDAY;
            case '5' -> DayOfWeek.THURSDAY;
            case '6' -> DayOfWeek.FRIDAY;
            case '7' -> DayOfWeek.SATURDAY;
            case '8' -> DayOfWeek.SUNDAY;
            default  -> null; // '0' = mỗi ngày
        };
    }

    /**
     * Kiểm tra EX_CODE có khớp với ngày sản xuất {@code productionDate} không.
     *
     * <p>dayChar='0' → luôn khớp (sản xuất hằng ngày).
     * dayChar='2'..'8' → khớp khi productionDate đúng thứ tương ứng.
     *
     * @param exCode        mã EX_CODE
     * @param groupCode     mã nhóm item (prefix)
     * @param productionDate ngày sản xuất cần kiểm tra
     */
    public static boolean matchesProductionDate(String exCode, String groupCode, LocalDate productionDate) {
        Character dayChar = extractDayChar(exCode, groupCode);
        if (dayChar == null) return false;
        if (dayChar == '0') return true; // sản xuất mỗi ngày → luôn match
        DayOfWeek dow = toDayOfWeek(dayChar);
        return dow != null && productionDate.getDayOfWeek() == dow;
    }
}
