package ir.clubcore.auth;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ir.clubcore.config.CurrentUser;
import ir.clubcore.member.MemberService;
import ir.clubcore.user.UserDto;
import ir.clubcore.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank(message = "شماره موبایل الزامی است") String phone,
            @NotBlank(message = "رمز عبور الزامی است") String password) {
    }

    public record OtpRequest(@NotBlank(message = "شماره موبایل الزامی است") String phone) {
    }

    public record OtpVerify(@NotBlank String phone, @NotBlank(message = "کد تأیید الزامی است") String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record RegisterRequest(@NotBlank(message = "نام الزامی است") @Size(max = 120) String fullName,
            @NotBlank(message = "شماره موبایل الزامی است") String phone,
            @NotBlank @Size(min = 8, max = 64, message = "رمز عبور باید حداقل ۸ کاراکتر باشد") String password,
            String referralCode) {
    }

    public record ChangePassword(String currentPassword,
            @NotBlank @Size(min = 8, max = 64, message = "رمز عبور باید حداقل ۸ کاراکتر باشد") String newPassword) {
    }

    private final AuthService auth;
    private final TokenService tokens;
    private final MemberService members;
    private final UserService users;
    private final CurrentUser currentUser;

    public AuthController(AuthService auth, TokenService tokens, MemberService members, UserService users,
            CurrentUser currentUser) {
        this.auth = auth;
        this.tokens = tokens;
        this.members = members;
        this.users = users;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public TokenService.Tokens login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req.phone(), req.password());
    }

    @PostMapping("/otp/request")
    public Map<String, String> requestOtp(@Valid @RequestBody OtpRequest req) {
        auth.requestOtp(req.phone());
        return Map.of("message", "در صورت ثبت بودن شماره، کد ورود ارسال شد");
    }

    @PostMapping("/otp/verify")
    public TokenService.Tokens verifyOtp(@Valid @RequestBody OtpVerify req) {
        return auth.verifyOtp(req.phone(), req.code());
    }

    @PostMapping("/refresh")
    public TokenService.Tokens refresh(@Valid @RequestBody RefreshRequest req) {
        return tokens.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody RefreshRequest req) {
        tokens.revoke(req.refreshToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenService.Tokens register(@Valid @RequestBody RegisterRequest req) {
        var member = members.selfRegister(req.fullName(), req.phone(), req.password(), req.referralCode());
        return tokens.issue(member.getUser());
    }

    @GetMapping("/me")
    public UserDto me() {
        return UserDto.of(users.get(currentUser.id()));
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePassword req) {
        users.changePassword(currentUser.id(), req.currentPassword(), req.newPassword());
    }
}
