package ir.clubcore.setting;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Digits;

@Service
public class SettingService {

    public static final String ACTIVE_GATEWAY = "payment.activeGateway";

    /** Free-text settings editable through the API. The active gateway has its own endpoint that validates it. */
    private static final Set<String> TEXT = Set.of("gym.name", "gym.phone", "gym.address");

    /** Numeric loyalty settings with their allowed ranges. */
    private static final Map<String, long[]> NUMERIC = Map.of(
            "loyalty.checkinPoints", new long[] {0, 1_000},
            "loyalty.tomanPerPoint", new long[] {1, 100_000_000},
            "loyalty.referralPoints", new long[] {0, 100_000},
            "loyalty.silverThreshold", new long[] {1, 10_000_000},
            "loyalty.goldThreshold", new long[] {1, 10_000_000},
            "loyalty.silverDiscountPercent", new long[] {0, 100},
            "loyalty.goldDiscountPercent", new long[] {0, 100});

    private final SettingRepository repo;

    public SettingService(SettingRepository repo) {
        this.repo = repo;
    }

    public String get(String key, String def) {
        return repo.findById(key).map(Setting::getValue).orElse(def);
    }

    public long getLong(String key, long def) {
        try {
            return Long.parseLong(get(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public Map<String, String> all() {
        Map<String, String> map = new TreeMap<>();
        repo.findAll().forEach(s -> map.put(s.getKey(), s.getValue()));
        return map;
    }

    public Map<String, String> publicInfo() {
        return Map.of("name", get("gym.name", ""), "phone", get("gym.phone", ""), "address", get("gym.address", ""));
    }

    @Transactional
    public Map<String, String> update(Map<String, String> values) {
        Map<String, String> clean = new TreeMap<>();
        values.forEach((k, v) -> {
            if (!TEXT.contains(k) && !NUMERIC.containsKey(k)) {
                throw new BusinessException("تنظیم ناشناخته: " + k);
            }
            if (v == null || v.isBlank()) {
                throw new BusinessException("مقدار تنظیم نمی‌تواند خالی باشد: " + k);
            }
            String value = Digits.toLatin(v.trim());
            if (TEXT.contains(k)) {
                if (value.length() > 200) {
                    throw new BusinessException("مقدار تنظیم بیش از حد طولانی است: " + k);
                }
            } else {
                long[] range = NUMERIC.get(k);
                long n;
                try {
                    n = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException("مقدار عددی نامعتبر برای " + k);
                }
                if (n < range[0] || n > range[1]) {
                    throw new BusinessException("مقدار " + k + " باید بین " + range[0] + " و " + range[1] + " باشد");
                }
                value = String.valueOf(n);
            }
            clean.put(k, value);
        });
        // Validate tier consistency against the values that will be in effect after the update.
        long silver = Long.parseLong(clean.getOrDefault("loyalty.silverThreshold", get("loyalty.silverThreshold", "1000")));
        long gold = Long.parseLong(clean.getOrDefault("loyalty.goldThreshold", get("loyalty.goldThreshold", "3000")));
        if (silver >= gold) {
            throw new BusinessException("آستانه سطح طلایی باید بیشتر از سطح نقره‌ای باشد");
        }
        long silverPct = Long.parseLong(clean.getOrDefault("loyalty.silverDiscountPercent",
                get("loyalty.silverDiscountPercent", "5")));
        long goldPct = Long.parseLong(clean.getOrDefault("loyalty.goldDiscountPercent",
                get("loyalty.goldDiscountPercent", "10")));
        if (silverPct > goldPct) {
            throw new BusinessException("درصد تخفیف سطح طلایی نباید کمتر از سطح نقره‌ای باشد");
        }
        clean.forEach(this::set);
        return all();
    }

    @Transactional
    public void set(String key, String value) {
        Setting s = repo.findById(key).orElseGet(() -> new Setting(key, value));
        s.setValue(value);
        repo.save(s);
    }
}
