package ir.clubcore.membership;

import java.time.Instant;
import java.time.LocalDate;

import ir.clubcore.billing.Invoice;
import ir.clubcore.member.Member;
import ir.clubcore.plan.Plan;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id")
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer sessionsTotal;
    private int sessionsUsed;
    private int freezeDaysUsed;
    private LocalDate frozenSince;

    @Enumerated(EnumType.STRING)
    private MembershipStatus status;

    private long price;
    private long discount;
    private Instant createdAt = Instant.now();

    public Integer sessionsRemaining() {
        return sessionsTotal == null ? null : Math.max(0, sessionsTotal - sessionsUsed);
    }

    public boolean hasSessionsLeft() {
        return sessionsTotal == null || sessionsUsed < sessionsTotal;
    }

    public boolean covers(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }
}
