package ir.clubcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String publicUrl, String zone, Jwt jwt, Qr qr, Bootstrap bootstrap, Payment payment,
        Sms sms, Http http, Ai ai) {

    public record Jwt(String secret, long accessTtlMinutes, long refreshTtlDays) {
    }

    public record Qr(long ttlSeconds) {
    }

    public record Bootstrap(String adminPhone, String adminPassword, String adminName, boolean seedDemo) {
    }

    public record Payment(boolean mockEnabled, Zarinpal zarinpal, Zibal zibal) {
        public record Zarinpal(String merchantId, boolean sandbox) {
        }

        public record Zibal(String merchant) {
        }
    }

    public record Sms(String provider, String kavenegarApiKey, String kavenegarSender) {
    }

    public record Http(int connectTimeoutSeconds, int readTimeoutSeconds) {
    }

    public record Ai(String apiKey, String model, long maxTokens, long churnCacheSeconds) {
        public boolean enabled() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
