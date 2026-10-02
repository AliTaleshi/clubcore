package ir.clubcore.member;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;

/**
 * Short-lived signed tokens rendered as the member's entry QR code. Format: {@code memberId.expEpoch.signature}.
 * Uses a key derived from the JWT secret but distinct from it, so a QR token can never be used as an API token.
 */
@Service
public class QrTokenService {

    public record QrToken(String token, long expiresAt) {
    }

    private final SecretKeySpec key;
    private final long ttlSeconds;
    private final Clock clock;

    public QrTokenService(AppProperties props, Clock clock) {
        this.key = new SecretKeySpec(sha256((props.jwt().secret() + ":qr-entry").getBytes(StandardCharsets.UTF_8)),
                "HmacSHA256");
        this.ttlSeconds = props.qr().ttlSeconds();
        this.clock = clock;
    }

    public QrToken generate(long memberId) {
        long exp = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = memberId + "." + exp;
        return new QrToken(payload + "." + sign(payload), exp);
    }

    /** Returns the member id, or throws if the token is malformed, forged or expired. */
    public long verify(String token) {
        BusinessException invalid = new BusinessException("کد QR نامعتبر یا منقضی شده است");
        if (token == null) {
            throw invalid;
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw invalid;
        }
        String payload = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw invalid;
        }
        try {
            long exp = Long.parseLong(parts[1]);
            if (exp < clock.instant().getEpochSecond()) {
                throw invalid;
            }
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw invalid;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] sig = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig).substring(0, 22);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
