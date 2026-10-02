package ir.clubcore.setting;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;

@Service
public class SettingService {

    public static final String ACTIVE_GATEWAY = "payment.activeGateway";

    /** Settings that may be edited through the API; anything else is rejected. */
    private static final Set<String> EDITABLE = Set.of("gym.name", "gym.phone", "gym.address", ACTIVE_GATEWAY,
            "loyalty.checkinPoints", "loyalty.tomanPerPoint", "loyalty.referralPoints", "loyalty.silverThreshold",
            "loyalty.goldThreshold", "loyalty.silverDiscountPercent", "loyalty.goldDiscountPercent");

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
        values.forEach((k, v) -> {
            if (!EDITABLE.contains(k)) {
                throw new BusinessException("تنظیم ناشناخته: " + k);
            }
            if (v == null || v.isBlank()) {
                throw new BusinessException("مقدار تنظیم نمی‌تواند خالی باشد: " + k);
            }
            if (k.startsWith("loyalty.")) {
                try {
                    if (Long.parseLong(v.trim()) < 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    throw new BusinessException("مقدار عددی نامعتبر برای " + k);
                }
            }
            set(k, v.trim());
        });
        return all();
    }

    @Transactional
    public void set(String key, String value) {
        Setting s = repo.findById(key).orElseGet(() -> new Setting(key, value));
        s.setValue(value);
        repo.save(s);
    }
}
