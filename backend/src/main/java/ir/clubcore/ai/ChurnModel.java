package ir.clubcore.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Local, explainable churn model: a logistic score over attendance and membership features. Weights were chosen from
 * common fitness-industry retention signals (recency of visits, declining frequency, upcoming expiry) so it works
 * without training data and gives human-readable reasons.
 */
public final class ChurnModel {

    public record Features(int daysSinceLastVisit, int visitsLast14, int visitsPrev28, Integer daysToExpiry,
            Integer sessionsRemaining, int tenureDays, int paidMemberships, boolean frozen) {
    }

    public record Score(int risk, List<String> reasons) {
    }

    private ChurnModel() {
    }

    public static Score score(Features f) {
        double z = -2.2;
        List<String> reasons = new ArrayList<>();

        int recency = Math.min(f.daysSinceLastVisit(), 60);
        z += 0.07 * recency;
        if (f.daysSinceLastVisit() >= 14) {
            reasons.add(f.daysSinceLastVisit() >= 60 ? "بیش از ۶۰ روز مراجعه نداشته است"
                    : f.daysSinceLastVisit() + " روز است که مراجعه نکرده است");
        }

        // Frequency trend: compare the last 2 weeks with the weekly-normalised previous 4 weeks.
        double prevPerTwoWeeks = f.visitsPrev28() / 2.0;
        if (prevPerTwoWeeks >= 2 && f.visitsLast14() < prevPerTwoWeeks * 0.5) {
            z += 1.1;
            reasons.add("تعداد مراجعات در دو هفته اخیر بیش از ۵۰٪ کاهش یافته است");
        } else if (f.visitsLast14() >= 6) {
            z -= 0.9;
        } else if (f.visitsLast14() >= 3) {
            z -= 0.4;
        }

        Integer dte = f.daysToExpiry();
        if (dte == null) {
            z += 1.0;
            reasons.add("اشتراک فعالی ندارد");
        } else if (dte < 0) {
            z += 1.6;
            reasons.add("اشتراک " + (-dte) + " روز پیش منقضی شده و تمدید نشده است");
        } else if (dte <= 7) {
            z += 0.9;
            reasons.add("اشتراک تا " + dte + " روز دیگر تمام می‌شود");
        }

        if (f.sessionsRemaining() != null && f.sessionsRemaining() <= 2) {
            z += 0.5;
            reasons.add("فقط " + f.sessionsRemaining() + " جلسه باقی مانده است");
        }
        if (f.frozen()) {
            z += 0.4;
            reasons.add("اشتراک در حالت فریز است");
        }
        if (f.tenureDays() < 45) {
            z += 0.4;
            reasons.add("عضو جدید است (دوره حساس ۴۵ روز اول)");
        }
        z -= 0.35 * Math.min(f.paidMemberships() - 1, 5);
        if (f.paidMemberships() >= 3 && reasons.isEmpty()) {
            reasons.add("عضو وفادار با سابقه چند تمدید");
        }

        int risk = (int) Math.round(100.0 / (1 + Math.exp(-z)));
        return new Score(Math.max(0, Math.min(100, risk)), reasons);
    }
}
