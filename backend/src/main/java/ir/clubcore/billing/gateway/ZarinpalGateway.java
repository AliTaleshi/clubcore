package ir.clubcore.billing.gateway;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Zarinpal REST v4. Amounts are sent in Toman via {@code currency=IRT}. */
public class ZarinpalGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(ZarinpalGateway.class);

    private final RestClient client;
    private final String merchantId;
    private final String host;

    public ZarinpalGateway(RestClient.Builder builder, String merchantId, boolean sandbox) {
        this.host = sandbox ? "https://sandbox.zarinpal.com" : "https://payment.zarinpal.com";
        this.client = builder.baseUrl(host).build();
        this.merchantId = merchantId;
    }

    @Override
    public GatewayType type() {
        return GatewayType.ZARINPAL;
    }

    @Override
    public String title() {
        return "زرین‌پال";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StartResult request(long amountToman, String description, String mobile, String callbackUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("merchant_id", merchantId);
        body.put("amount", amountToman);
        body.put("currency", "IRT");
        body.put("description", description);
        body.put("callback_url", callbackUrl);
        if (mobile != null) {
            body.put("metadata", Map.of("mobile", mobile));
        }
        Map<String, Object> res = post("/pg/v4/payment/request.json", body);
        Object dataObj = res == null ? null : res.get("data");
        if (dataObj instanceof Map<?, ?> data && Integer.valueOf(100).equals(asInt(data.get("code")))) {
            String authority = String.valueOf(data.get("authority"));
            return new StartResult(authority, host + "/pg/StartPay/" + authority);
        }
        log.warn("Zarinpal request failed: {}", res);
        throw new GatewayException("خطا در اتصال به درگاه زرین‌پال");
    }

    @Override
    public String authorityFrom(Map<String, String> params) {
        return params.get("Authority");
    }

    @Override
    public boolean callbackSuccessful(Map<String, String> params) {
        return "OK".equalsIgnoreCase(params.get("Status"));
    }

    @Override
    public VerifyResult verify(String authority, long amountToman) {
        Map<String, Object> body = Map.of("merchant_id", merchantId, "amount", amountToman, "authority", authority);
        Map<String, Object> res;
        try {
            res = post("/pg/v4/payment/verify.json", body);
        } catch (GatewayException e) {
            return VerifyResult.failed(e.getMessage());
        }
        Object dataObj = res == null ? null : res.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Integer code = asInt(data.get("code"));
            if (code != null && (code == 100 || code == 101)) {
                return new VerifyResult(true, String.valueOf(data.get("ref_id")),
                        data.get("card_pan") == null ? null : String.valueOf(data.get("card_pan")), "OK");
            }
        }
        return VerifyResult.failed("تراکنش توسط زرین‌پال تأیید نشد");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(body).retrieve()
                    .onStatus(s -> s.is4xxClientError(), (req, resp) -> {
                    })
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Zarinpal call {} failed: {}", path, e.getMessage());
            throw new GatewayException("خطا در اتصال به درگاه زرین‌پال");
        }
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return o == null ? null : Integer.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
