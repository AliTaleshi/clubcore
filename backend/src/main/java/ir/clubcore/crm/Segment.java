package ir.clubcore.crm;

public enum Segment {
    ALL_ACTIVE("همه اعضای فعال"),
    EXPIRING_SOON("اشتراک رو به اتمام (۷ روز)"),
    EXPIRED_RECENTLY("منقضی‌شده در ۳۰ روز اخیر"),
    INACTIVE("بدون مراجعه در ۱۴ روز اخیر"),
    HIGH_CHURN_RISK("ریسک بالای ریزش (هوش مصنوعی)"),
    BIRTHDAY_THIS_WEEK("تولد در هفته جاری");

    private final String title;

    Segment(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
