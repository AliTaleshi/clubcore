package ir.clubcore.member;

import java.time.Instant;
import java.time.LocalDate;

public record MemberDto(Long id, Long userId, String fullName, String phone, String membershipNo, String cardNo,
        String nationalCode, Gender gender, LocalDate birthDate, String address, String emergencyPhone, Long coachId,
        String coachName, String referralCode, String goal, String notes, boolean active, Instant createdAt) {

    public static MemberDto of(Member m) {
        return new MemberDto(m.getId(), m.getUser().getId(), m.getFullName(), m.getPhone(), m.getMembershipNo(),
                m.getCardNo(), m.getNationalCode(), m.getGender(), m.getBirthDate(), m.getAddress(),
                m.getEmergencyPhone(), m.getCoach() == null ? null : m.getCoach().getId(),
                m.getCoach() == null ? null : m.getCoach().getFullName(), m.getReferralCode(), m.getGoal(),
                m.getNotes(), m.getUser().isActive(), m.getCreatedAt());
    }
}
