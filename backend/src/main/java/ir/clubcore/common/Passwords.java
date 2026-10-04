package ir.clubcore.common;

import java.nio.charset.StandardCharsets;

public final class Passwords {

    /** BCrypt only uses the first 72 bytes and Spring Security rejects longer input. */
    private static final int MAX_BYTES = 72;

    private Passwords() {
    }

    public static void validate(String raw) {
        if (raw == null || raw.length() < 8) {
            throw new BusinessException("رمز عبور باید حداقل ۸ کاراکتر باشد");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessException("رمز عبور بیش از حد طولانی است");
        }
    }
}
