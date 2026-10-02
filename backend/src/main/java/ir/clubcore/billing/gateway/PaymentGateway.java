package ir.clubcore.billing.gateway;

import java.util.Map;

/** Online payment provider. Amounts are always passed in Toman; implementations convert if needed. */
public interface PaymentGateway {

    record StartResult(String authority, String redirectUrl) {
    }

    record VerifyResult(boolean success, String refId, String cardPan, String message) {
        public static VerifyResult failed(String message) {
            return new VerifyResult(false, null, null, message);
        }
    }

    GatewayType type();

    /** Persian display name. */
    String title();

    StartResult request(long amountToman, String description, String mobile, String callbackUrl);

    /** Extracts the transaction identifier from callback query parameters. */
    String authorityFrom(Map<String, String> callbackParams);

    /** Whether the user completed payment on the gateway page (before server-side verification). */
    boolean callbackSuccessful(Map<String, String> callbackParams);

    VerifyResult verify(String authority, long amountToman);
}
