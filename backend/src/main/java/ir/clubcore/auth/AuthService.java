package ir.clubcore.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Codes;
import ir.clubcore.common.Phones;
import ir.clubcore.notification.SmsService;
import ir.clubcore.setting.SettingService;
import ir.clubcore.user.User;
import ir.clubcore.user.UserRepository;

@Service
public class AuthService {

    static final Duration OTP_TTL = Duration.ofMinutes(2);
    static final Duration OTP_RESEND = Duration.ofSeconds(60);
    static final int OTP_MAX_ATTEMPTS = 5;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final OtpCodeRepository otps;
    private final SmsService sms;
    private final SettingService settings;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, OtpCodeRepository otps,
            SmsService sms, SettingService settings, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
        this.otps = otps;
        this.sms = sms;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional
    public TokenService.Tokens login(String phone, String password) {
        User user = users.findByPhone(Phones.normalize(phone))
                .filter(u -> u.getPasswordHash() != null && encoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "شماره موبایل یا رمز عبور اشتباه است"));
        ensureActive(user);
        return tokens.issue(user);
    }

    /** Sends a one-time code. Unknown numbers get the same response so phone numbers cannot be enumerated. */
    @Transactional
    public void requestOtp(String rawPhone) {
        String phone = Phones.normalize(rawPhone);
        if (!Phones.isValid(phone)) {
            throw new BusinessException("شماره موبایل نامعتبر است");
        }
        Instant now = clock.instant();
        otps.findFirstByPhoneOrderByIdDesc(phone)
                .filter(o -> o.getCreatedAt().plus(OTP_RESEND).isAfter(now))
                .ifPresent(o -> {
                    throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "لطفاً یک دقیقه بعد دوباره تلاش کنید");
                });
        if (users.findByPhone(phone).filter(User::isActive).isEmpty()) {
            return;
        }
        String code = Codes.numeric(5);
        OtpCode otp = new OtpCode();
        otp.setPhone(phone);
        otp.setCodeHash(encoder.encode(code));
        otp.setCreatedAt(now);
        otp.setExpiresAt(now.plus(OTP_TTL));
        otps.save(otp);
        sms.send(phone, "کد ورود شما به " + settings.get("gym.name", "باشگاه") + ": " + code);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TokenService.Tokens verifyOtp(String rawPhone, String code) {
        String phone = Phones.normalize(rawPhone);
        BusinessException invalid = new BusinessException(HttpStatus.UNAUTHORIZED, "کد وارد شده نامعتبر یا منقضی است");
        OtpCode otp = otps.findFirstByPhoneOrderByIdDesc(phone)
                .filter(o -> !o.isUsed() && o.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> invalid);
        if (otp.getAttempts() >= OTP_MAX_ATTEMPTS) {
            throw invalid;
        }
        otp.setAttempts(otp.getAttempts() + 1);
        if (code == null || !encoder.matches(ir.clubcore.common.Digits.toLatin(code.trim()), otp.getCodeHash())) {
            throw invalid;
        }
        otp.setUsed(true);
        User user = users.findByPhone(phone).orElseThrow(() -> invalid);
        ensureActive(user);
        return tokens.issue(user);
    }

    private static void ensureActive(User user) {
        if (!user.isActive()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "حساب کاربری شما غیرفعال شده است");
        }
    }
}
