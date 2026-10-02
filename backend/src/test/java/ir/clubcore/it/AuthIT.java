package ir.clubcore.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ir.clubcore.notification.SmsLogRepository;
import ir.clubcore.support.Api;
import ir.clubcore.support.ItBase;

class AuthIT extends ItBase {

    @Autowired
    SmsLogRepository smsLogs;

    @Test
    void loginReturnsTokensAndProfile() {
        var res = api.post("/api/auth/login", null, Map.of("phone", "۰۹۱۲۰۰۰۰۰۰۰", "password", "Admin@12345"))
                .expect(200);
        assertThat((String) res.json("$.accessToken")).isNotBlank();
        assertThat((String) res.json("$.user.role")).isEqualTo("ADMIN");
        api.get("/api/auth/me", (String) res.json("$.accessToken")).expect(200);
    }

    @Test
    void wrongPasswordIsRejectedWithPersianMessage() {
        var res = api.post("/api/auth/login", null, Map.of("phone", "09120000000", "password", "nope")).expect(401);
        assertThat((String) res.json("$.detail")).contains("اشتباه");
    }

    @Test
    void refreshTokenRotatesAndOldTokenIsRevoked() {
        var login = api.post("/api/auth/login", null, Map.of("phone", "09120000000", "password", "Admin@12345"));
        String refresh = login.json("$.refreshToken");
        var refreshed = api.post("/api/auth/refresh", null, Map.of("refreshToken", refresh)).expect(200);
        assertThat((String) refreshed.json("$.refreshToken")).isNotEqualTo(refresh);
        api.post("/api/auth/refresh", null, Map.of("refreshToken", refresh)).expect(401);
    }

    @Test
    void logoutRevokesRefreshToken() {
        var login = api.post("/api/auth/login", null, Map.of("phone", "09120000000", "password", "Admin@12345"));
        String refresh = login.json("$.refreshToken");
        api.post("/api/auth/logout", null, Map.of("refreshToken", refresh)).expect(200);
        api.post("/api/auth/refresh", null, Map.of("refreshToken", refresh)).expect(401);
    }

    @Test
    void otpLoginFlow() {
        var member = createMember(admin, "Secret123");
        String phone = (String) member.get("phone");
        api.post("/api/auth/otp/request", null, Map.of("phone", phone)).expect(200);
        // Resend within a minute is throttled.
        api.post("/api/auth/otp/request", null, Map.of("phone", phone)).expect(429);
        String msg = smsLogs.findTop20ByPhoneOrderByIdDesc(phone).get(0).getMessage();
        String code = msg.substring(msg.lastIndexOf(' ') + 1);

        api.post("/api/auth/otp/verify", null, Map.of("phone", phone, "code", "00000")).expect(401);
        var ok = api.post("/api/auth/otp/verify", null, Map.of("phone", phone, "code", code)).expect(200);
        assertThat((String) ok.json("$.user.role")).isEqualTo("MEMBER");
        // A code works only once.
        api.post("/api/auth/otp/verify", null, Map.of("phone", phone, "code", code)).expect(401);
    }

    @Test
    void otpForUnknownNumberDoesNotRevealAccountExistence() {
        String phone = Api.phone();
        api.post("/api/auth/otp/request", null, Map.of("phone", phone)).expect(200);
        assertThat(smsLogs.findTop20ByPhoneOrderByIdDesc(phone)).isEmpty();
    }

    @Test
    void selfRegistrationWithReferralCode() {
        var referrer = createMember(admin, "Secret123");
        String referrerToken = api.login((String) referrer.get("phone"), "Secret123");
        String code = api.get("/api/me/member", referrerToken).expect(200).json("$.member.referralCode");

        String phone = Api.phone();
        var res = api.post("/api/auth/register", null,
                Map.of("fullName", "کاربر جدید", "phone", phone, "password", "Secret123", "referralCode", code))
                .expect(201);
        assertThat((String) res.json("$.user.role")).isEqualTo("MEMBER");
        // Duplicate phone and bad referral codes are rejected.
        api.post("/api/auth/register", null, Map.of("fullName", "تکراری", "phone", phone, "password", "Secret123"))
                .expect(409);
        api.post("/api/auth/register", null,
                Map.of("fullName", "x", "phone", Api.phone(), "password", "Secret123", "referralCode", "NOPE99"))
                .expect(400);
    }

    @Test
    void changePasswordRequiresCurrentPassword() {
        var m = createMember(admin, "Secret123");
        String token = api.login((String) m.get("phone"), "Secret123");
        api.post("/api/auth/change-password", token, Map.of("currentPassword", "bad", "newPassword", "NewSecret1"))
                .expect(400);
        api.post("/api/auth/change-password", token,
                Map.of("currentPassword", "Secret123", "newPassword", "NewSecret1")).expect(200);
        api.login((String) m.get("phone"), "NewSecret1");
    }

    @Test
    void validationErrorsAreReportedPerField() {
        var res = api.post("/api/auth/register", null, Map.of("fullName", "", "phone", "", "password", "x"))
                .expect(400);
        assertThat((Map<String, Object>) res.json("$.errors")).containsKeys("fullName", "password");
    }
}
