package ir.clubcore.common;

import java.time.LocalDate;

public final class DateRange {

    private DateRange() {
    }

    /** Rejects reversed ranges. */
    public static void check(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("تاریخ شروع بازه نباید بعد از تاریخ پایان باشد");
        }
    }

    /** Rejects reversed ranges and ranges longer than {@code maxDays} (for reports that produce one row per day). */
    public static void check(LocalDate from, LocalDate to, int maxDays) {
        check(from, to);
        if (from.plusDays(maxDays).isBefore(to)) {
            throw new BusinessException("بازه گزارش حداکثر " + maxDays + " روز است");
        }
    }
}
