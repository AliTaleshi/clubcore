package ir.clubcore.billing.gateway;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Simulated gateway for development and automated tests. Redirects to the SPA page {@code /mock-gateway}, which lets
 * the user choose success or failure and then calls the regular callback endpoint.
 */
public class MockGateway implements PaymentGateway {

    private final String publicUrl;

    public MockGateway(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    @Override
    public GatewayType type() {
        return GatewayType.MOCK;
    }

    @Override
    public String title() {
        return "درگاه آزمایشی";
    }

    @Override
    public StartResult request(long amountToman, String description, String mobile, String callbackUrl) {
        String authority = "MOCK-" + UUID.randomUUID();
        String url = publicUrl + "/mock-gateway?authority=" + authority + "&amount=" + amountToman + "&callback="
                + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8) + "&description="
                + URLEncoder.encode(description, StandardCharsets.UTF_8);
        return new StartResult(authority, url);
    }

    @Override
    public String authorityFrom(Map<String, String> params) {
        return params.get("authority");
    }

    @Override
    public boolean callbackSuccessful(Map<String, String> params) {
        return "OK".equalsIgnoreCase(params.get("status"));
    }

    @Override
    public VerifyResult verify(String authority, long amountToman) {
        if (authority == null || !authority.startsWith("MOCK-")) {
            return VerifyResult.failed("تراکنش آزمایشی نامعتبر است");
        }
        return new VerifyResult(true, String.valueOf(Math.abs(authority.hashCode())), "6037-99**-****-1234", "OK");
    }
}
