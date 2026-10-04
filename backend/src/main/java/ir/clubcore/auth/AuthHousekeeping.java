package ir.clubcore.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes expired or revoked refresh tokens and old one-time codes so these tables don't grow forever. */
@Service
public class AuthHousekeeping {

    private final RefreshTokenRepository refreshTokens;
    private final OtpCodeRepository otps;
    private final Clock clock;

    public AuthHousekeeping(RefreshTokenRepository refreshTokens, OtpCodeRepository otps, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.otps = otps;
        this.clock = clock;
    }

    @Transactional
    public int purge() {
        var now = clock.instant();
        // Keep a day of OTP rows so the resend throttle and attempt counters still apply.
        return refreshTokens.purge(now) + otps.purge(now.minus(Duration.ofDays(1)));
    }
}
