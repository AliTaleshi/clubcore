package ir.clubcore.crm;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import ir.clubcore.ai.ChurnService;
import ir.clubcore.attendance.AttendanceRepository;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.membership.Membership;
import ir.clubcore.membership.MembershipRepository;
import ir.clubcore.membership.MembershipStatus;

/** Resolves CRM audience segments to concrete members. */
@Service
public class SegmentService {

    private final MembershipRepository memberships;
    private final MemberRepository members;
    private final AttendanceRepository attendance;
    private final ChurnService churn;
    private final Clock clock;

    public SegmentService(MembershipRepository memberships, MemberRepository members, AttendanceRepository attendance,
            ChurnService churn, Clock clock) {
        this.memberships = memberships;
        this.members = members;
        this.attendance = attendance;
        this.churn = churn;
        this.clock = clock;
    }

    public List<Member> resolve(Segment segment) {
        LocalDate today = LocalDate.now(clock);
        return switch (segment) {
            case ALL_ACTIVE -> activeMembers(today);
            case EXPIRING_SOON -> distinct(memberships.expiringBetween(today, today.plusDays(7)));
            case EXPIRED_RECENTLY -> members.findAll().stream().filter(m -> {
                var list = memberships.findByMemberIdOrderByIdDesc(m.getId());
                boolean hasCurrent = list.stream().anyMatch(ms -> ms.getStatus() == MembershipStatus.ACTIVE
                        || ms.getStatus() == MembershipStatus.FROZEN);
                return !hasCurrent && list.stream().anyMatch(ms -> ms.getStatus() == MembershipStatus.EXPIRED
                        && !ms.getEndDate().isBefore(today.minusDays(30)));
            }).toList();
            case INACTIVE -> {
                Instant cutoff = today.minusDays(14).atStartOfDay(clock.getZone()).toInstant();
                yield activeMembers(today).stream().filter(m -> {
                    Instant last = attendance.lastVisit(m.getId());
                    return last == null || last.isBefore(cutoff);
                }).toList();
            }
            case HIGH_CHURN_RISK -> churn.scoreAll().stream().filter(s -> s.level() == ChurnService.Level.HIGH)
                    .map(s -> members.findById(s.memberId()).orElse(null)).filter(m -> m != null).toList();
            case BIRTHDAY_THIS_WEEK -> members.findAll().stream().filter(m -> m.getBirthDate() != null).filter(m -> {
                MonthDay bd = MonthDay.from(m.getBirthDate());
                for (int i = 0; i < 7; i++) {
                    if (MonthDay.from(today.plusDays(i)).equals(bd)) {
                        return true;
                    }
                }
                return false;
            }).toList();
        };
    }

    private List<Member> activeMembers(LocalDate today) {
        return members.findAll().stream().filter(m -> m.getUser().isActive())
                .filter(m -> memberships.findByMemberIdAndStatusIn(m.getId(),
                        Set.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN)).stream()
                        .anyMatch(ms -> !ms.getEndDate().isBefore(today)))
                .toList();
    }

    private static List<Member> distinct(List<Membership> list) {
        Set<Member> set = new LinkedHashSet<>();
        list.forEach(ms -> set.add(ms.getMember()));
        return List.copyOf(set);
    }
}
