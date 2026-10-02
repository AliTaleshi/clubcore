package ir.clubcore.billing.gateway;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Zibal REST API. Zibal works in Rial, so Toman amounts are multiplied by 10. Merchant "zibal" is the test merchant. */
public class ZibalGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(ZibalGateway.class);
    private static final String HOST = "https://gateway.zibal.ir";

    private final RestClient client;
    private final String merchant;

    public ZibalGateway(RestClient.Builder builder, String merchant) {
        this.client = builder.baseUrl(HOST).build();
        this.merchant = merchant;
    }

    @Override
    public GatewayType type() {
        return GatewayType.ZIBAL;
    }

    @Override
    public String title() {
        return "زیبال";
    }

    @Override
    public StartResult request(long amountToman, String description, String mobile, String callbackUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("merchant", merchant);
        body.put("amount", amountToman * 10);
        body.put("callbackUrl", callbackUrl);
        body.put("description", description);
        if (mobile != null) {
            body.put("mobile", mobile);
        }
        Map<String, Object> res = post("/v1/request", body);
        if (res != null && Integer.valueOf(100).equals(asInt(res.get("result")))) {
            String trackId = String.valueOf(res.get("trackId"));
            return new StartResult(trackId, HOST + "/start/" + trackId);
        }
        log.warn("Zibal request failed: {}", res);
        throw new GatewayException("خطا در اتصال به درگاه زیبال");
    }

    @Override
    public String authorityFrom(Map<String, String> params) {
        return params.get("trackId");
    }

    @Override
    public boolean callbackSuccessful(Map<String, String> params) {
        return "1".equals(params.get("success"));
    }

    @Override
    public VerifyResult verify(String authority, long amountToman) {
        Map<String, Object> res;
        try {
            res = post("/v1/verify", Map.of("merchant", merchant, "trackId", Long.valueOf(authority)));
        } catch (GatewayException | NumberFormatException e) {
            return VerifyResult.failed("تراکنش توسط زیبال تأیید نشد");
        }
        Integer result = res == null ? null : asInt(res.get("result"));
        if (result != null && (result == 100 || result == 201)) {
            Object amount = res.get("amount");
            if (amount != null && result == 100 && asLong(amount) != amountToman * 10) {
                return VerifyResult.failed("مبلغ تراکنش با فاکتور مطابقت ندارد");
            }
            return new VerifyResult(true, String.valueOf(res.get("refNumber")),
                    res.get("cardNumber") == null ? null : String.valueOf(res.get("cardNumber")), "OK");
        }
        return VerifyResult.failed("تراکنش توسط زیبال تأیید نشد");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            return client.post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(Map.class);
        } catch (Exception e) {
            log.warn("Zibal call {} failed: {}", path, e.getMessage());
            throw new GatewayException("خطا در اتصال به درگاه زیبال");
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

    private static long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : Long.parseLong(o.toString());
    }
}
