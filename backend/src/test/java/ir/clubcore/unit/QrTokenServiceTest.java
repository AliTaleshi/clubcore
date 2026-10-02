package ir.clubcore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;
import ir.clubcore.member.QrTokenService;

class QrTokenServiceTest {

    private static AppProperties props() {
        return new AppProperties("http://x", "Asia/Tehran",
                new AppProperties.Jwt("unit-test-secret-0123456789abcdef0123456789", 30, 14),
                new AppProperties.Qr(60), null, null, null, null);
    }

    private static Clock at(long epochSecond) {
        return Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneId.of("Asia/Tehran"));
    }

    @Test
    void roundTripsMemberId() {
        var svc = new QrTokenService(props(), at(1_000_000));
        var token = svc.generate(42);
        assertThat(token.expiresAt()).isEqualTo(1_000_060);
        assertThat(svc.verify(token.token())).isEqualTo(42);
    }

    @Test
    void rejectsExpiredToken() {
        String token = new QrTokenService(props(), at(1_000_000)).generate(7).token();
        var later = new QrTokenService(props(), at(1_000_061));
        assertThatThrownBy(() -> later.verify(token)).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsTamperedMemberId() {
        var svc = new QrTokenService(props(), at(1_000_000));
        String token = svc.generate(7).token();
        String forged = "8" + token.substring(1);
        assertThatThrownBy(() -> svc.verify(forged)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> svc.verify("garbage")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> svc.verify(null)).isInstanceOf(BusinessException.class);
    }
}
