package ir.clubcore.member;

import java.time.LocalDate;

import ir.clubcore.common.Phones;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRequest(
        @NotBlank(message = "نام و نام خانوادگی الزامی است") @Size(max = 120) String fullName,
        @NotBlank(message = "شماره موبایل الزامی است") @Pattern(regexp = Phones.REGEX, message = "شماره موبایل نامعتبر است") String phone,
        @Size(min = 8, max = 64, message = "رمز عبور باید حداقل ۸ کاراکتر باشد") String password,
        @Pattern(regexp = "^$|^\\d{10}$", message = "کد ملی باید ۱۰ رقم باشد") String nationalCode,
        Gender gender,
        LocalDate birthDate,
        @Size(max = 300) String address,
        @Pattern(regexp = "^$|" + Phones.REGEX, message = "شماره اضطراری نامعتبر است") String emergencyPhone,
        Long coachId,
        @Size(max = 40) String cardNo,
        @Size(max = 12) String referralCode,
        @Size(max = 300) String goal,
        String notes,
        Boolean active) {
}
