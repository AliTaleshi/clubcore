package ir.clubcore.loyalty;

public enum Tier {
    BRONZE("برنزی"), SILVER("نقره‌ای"), GOLD("طلایی");

    private final String title;

    Tier(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
