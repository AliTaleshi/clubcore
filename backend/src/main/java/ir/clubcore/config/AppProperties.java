package ir.clubcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicUrl, String zone, Jwt jwt, Qr qr, Bootstrap bootstrap, Payment payment,
        Sms sms, Ai ai) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {
    }

    public record Qr(long ttlSeconds) {
    }

    public record Bootstrap(String adminPhone, String adminPassword, String adminName, boolean seedDemo) {
    }

    public record Payment(Zarinpal zarinpal, Zibal zibal) {
        public record Zarinpal(String merchantId, boolean sandbox) {
        }

        public record Zibal(String merchant) {
        }
    }

    public record Sms(String provider, String kavenegarApiKey, String kavenegarSender) {
    }

    public record Ai(String apiKey, String model, long maxTokens) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
