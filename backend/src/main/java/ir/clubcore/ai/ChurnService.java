package ir.clubcore.ai;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.attendance.AttendanceRepository;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.membership.Membership;
import ir.clubcore.membership.MembershipRepository;
import ir.clubcore.membership.MembershipStatus;

@Service
public class ChurnService {

    public enum Level {
        LOW, MEDIUM, HIGH
    }

    public record MemberRisk(Long memberId, String fullName, String phone, String membershipNo, int risk, Level level,
            List<String> reasons, LocalDate lastVisit, LocalDate membershipEnd) {
    }

    private static final EnumSet<MembershipStatus> PAID = EnumSet.of(MembershipStatus.ACTIVE, MembershipStatus.FROZEN,
            MembershipStatus.EXPIRED);

    private final MemberRepository members;
    private final MembershipRepository memberships;
    private final AttendanceRepository attendance;
    private final Clock clock;

    public ChurnService(MemberRepository members, MembershipRepository memberships, AttendanceRepository attendance,
            Clock clock) {
        this.members = members;
        this.memberships = memberships;
        this.attendance = attendance;
        this.clock = clock;
    }

    /** Scores members who are current or lapsed within 30 days, highest risk first. */
    @Transactional(readOnly = true)
    public List<MemberRisk> scoreAll() {
        LocalDate today = LocalDate.now(clock);
        return members.findAll().stream().filter(m -> m.getUser().isActive())
                .map(m -> score(m, today))
                .filter(r -> r != null)
                .sorted(Comparator.comparingInt(MemberRisk::risk).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public MemberRisk score(Member m) {
        return score(m, LocalDate.now(clock));
    }

    private MemberRisk score(Member m, LocalDate today) {
        List<Membership> history = memberships.findByMemberIdOrderByIdDesc(m.getId()).stream()
                .filter(ms -> PAID.contains(ms.getStatus())).toList();
        if (history.isEmpty()) {
            return null;
        }
        Membership latest = history.stream().max(Comparator.comparing(Membership::getEndDate)).orElseThrow();
        if (latest.getEndDate().isBefore(today.minusDays(30))) {
            return null;
        }
        ZoneId zone = clock.getZone();
        Instant now = clock.instant();
        Instant last = attendance.lastVisit(m.getId());
        Instant joined = m.getCreatedAt();
        int daysSince = (int) ChronoUnit.DAYS.between(last != null ? last : joined, now);
        int last14 = (int) attendance.countByMemberIdAndCheckInAtBetween(m.getId(), now.minus(14, ChronoUnit.DAYS), now);
        int prev28 = (int) attendance.countByMemberIdAndCheckInAtBetween(m.getId(), now.minus(42, ChronoUnit.DAYS),
                now.minus(14, ChronoUnit.DAYS));
        boolean current = latest.getStatus() != MembershipStatus.EXPIRED;
        Integer dte = (int) ChronoUnit.DAYS.between(today, latest.getEndDate());
        if (!current && dte >= 0) {
            dte = -1;
        }
        var features = new ChurnModel.Features(daysSince, last14, prev28, dte,
                current ? latest.sessionsRemaining() : null, (int) ChronoUnit.DAYS.between(joined, now),
                history.size(), latest.getStatus() == MembershipStatus.FROZEN);
        ChurnModel.Score s = ChurnModel.score(features);
        Level level = s.risk() >= 65 ? Level.HIGH : s.risk() >= 35 ? Level.MEDIUM : Level.LOW;
        return new MemberRisk(m.getId(), m.getFullName(), m.getPhone(), m.getMembershipNo(), s.risk(), level,
                s.reasons(), last == null ? null : LocalDate.ofInstant(last, zone), latest.getEndDate());
    }
}
