package ir.clubcore.membership;

import java.time.Instant;
import java.time.LocalDate;

public record MembershipDto(Long id, Long memberId, String memberName, Long planId, String planName,
        LocalDate startDate, LocalDate endDate, Integer sessionsTotal, int sessionsUsed, Integer sessionsRemaining,
        int freezeDaysUsed, int maxFreezeDays, LocalDate frozenSince, MembershipStatus status, long price,
        long discount, Long invoiceId, Instant createdAt) {

    public static MembershipDto of(Membership m) {
        return new MembershipDto(m.getId(), m.getMember().getId(), m.getMember().getFullName(), m.getPlan().getId(),
                m.getPlan().getName(), m.getStartDate(), m.getEndDate(), m.getSessionsTotal(), m.getSessionsUsed(),
                m.sessionsRemaining(), m.getFreezeDaysUsed(), m.getPlan().getMaxFreezeDays(), m.getFrozenSince(),
                m.getStatus(), m.getPrice(), m.getDiscount(), m.getInvoice() == null ? null : m.getInvoice().getId(),
                m.getCreatedAt());
    }
}
