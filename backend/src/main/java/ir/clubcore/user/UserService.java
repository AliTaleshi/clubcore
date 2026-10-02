package ir.clubcore.user;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.common.BusinessException;
import ir.clubcore.common.Phones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public record StaffRequest(@NotBlank(message = "نام الزامی است") @Size(max = 120) String fullName,
            @NotBlank @Pattern(regexp = Phones.REGEX, message = "شماره موبایل نامعتبر است") String phone,
            @NotNull(message = "نقش الزامی است") Role role,
            @Size(min = 8, max = 64, message = "رمز عبور باید حداقل ۸ کاراکتر باشد") String password,
            Boolean active) {
    }

    /** Creates a user, or fails if the phone is taken. Shared by staff creation and member registration. */
    @Transactional
    public User create(String phone, String fullName, Role role, String rawPassword) {
        String normalized = Phones.normalize(phone);
        if (!Phones.isValid(normalized)) {
            throw new BusinessException("شماره موبایل نامعتبر است");
        }
        if (users.existsByPhone(normalized)) {
            throw BusinessException.conflict("این شماره موبایل قبلاً ثبت شده است");
        }
        User u = new User(normalized, fullName.trim(), role);
        if (rawPassword != null && !rawPassword.isBlank()) {
            u.setPasswordHash(encoder.encode(rawPassword));
        }
        return users.save(u);
    }

    public List<UserDto> listStaff() {
        return users.findByRoleNotOrderByIdAsc(Role.MEMBER).stream().map(UserDto::of).toList();
    }

    public List<UserDto> listByRole(Role role) {
        return users.findByRoleAndActiveTrue(role).stream().map(UserDto::of).toList();
    }

    @Transactional
    public UserDto createStaff(StaffRequest req) {
        if (req.role() == Role.MEMBER) {
            throw new BusinessException("برای ثبت عضو از بخش اعضا استفاده کنید");
        }
        if (req.password() == null) {
            throw new BusinessException("رمز عبور الزامی است");
        }
        return UserDto.of(create(req.phone(), req.fullName(), req.role(), req.password()));
    }

    @Transactional
    public UserDto updateStaff(Long id, StaffRequest req, Long actingUserId) {
        User u = get(id);
        if (u.getRole() == Role.MEMBER || req.role() == Role.MEMBER) {
            throw new BusinessException("این کاربر کارمند نیست");
        }
        if (id.equals(actingUserId) && (req.role() != Role.ADMIN || Boolean.FALSE.equals(req.active()))) {
            throw new BusinessException("نمی‌توانید نقش یا وضعیت حساب خودتان را تغییر دهید");
        }
        String phone = Phones.normalize(req.phone());
        if (!phone.equals(u.getPhone()) && users.existsByPhone(phone)) {
            throw BusinessException.conflict("این شماره موبایل قبلاً ثبت شده است");
        }
        u.setPhone(phone);
        u.setFullName(req.fullName().trim());
        u.setRole(req.role());
        if (req.active() != null) {
            u.setActive(req.active());
        }
        if (req.password() != null && !req.password().isBlank()) {
            u.setPasswordHash(encoder.encode(req.password()));
        }
        return UserDto.of(u);
    }

    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> BusinessException.notFound("کاربر"));
    }

    @Transactional
    public void changePassword(Long userId, String current, String next) {
        User u = get(userId);
        if (u.getPasswordHash() != null && (current == null || !encoder.matches(current, u.getPasswordHash()))) {
            throw new BusinessException("رمز عبور فعلی اشتباه است");
        }
        u.setPasswordHash(encoder.encode(next));
    }
}
