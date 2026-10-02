package ir.clubcore.dashboard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.accounting.AccountingService;
import ir.clubcore.ai.ChurnService;
import ir.clubcore.attendance.AttendanceRepository;
import ir.clubcore.billing.InvoiceRepository;
import ir.clubcore.billing.InvoiceStatus;
import ir.clubcore.crm.LeadRepository;
import ir.clubcore.member.MemberRepository;
import ir.clubcore.membership.MembershipDto;
import ir.clubcore.membership.MembershipRepository;

/** Business KPIs shared by the staff dashboard and the AI insights feature. */
@Service
public class KpiService {

    public record Snapshot(long activeMembers, long presentNow, long todayVisits, long visitsLast30,
            long visitsPrev30, long revenueToday, long revenueLast30, long revenuePrev30, long expenseLast30,
            long newMembersLast30, long expiringIn7Days, long unpaidInvoices, long leadsDueToday,
            long highChurnRisk, long mediumChurnRisk) {
    }

    private final MembershipRepository memberships;
    private final AttendanceRepository attendance;
    private final InvoiceRepository invoices;
    private final MemberRepository members;
    private final LeadRepository leads;
    private final AccountingService accounting;
    private final ChurnService churn;
    private final Clock clock;

    public KpiService(MembershipRepository memberships, AttendanceRepository attendance, InvoiceRepository invoices,
            MemberRepository members, LeadRepository leads, AccountingService accounting, ChurnService churn,
            Clock clock) {
        this.memberships = memberships;
        this.attendance = attendance;
        this.invoices = invoices;
        this.members = members;
        this.leads = leads;
        this.accounting = accounting;
        this.churn = churn;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant startToday = today.atStartOfDay(zone).toInstant();
        Instant now = clock.instant();
        Instant d30 = today.minusDays(29).atStartOfDay(zone).toInstant();
        Instant d60 = today.minusDays(59).atStartOfDay(zone).toInstant();
        long expense30 = accounting.series(today.minusDays(29), today).stream()
                .mapToLong(AccountingService.SeriesPoint::expense).sum();
        var risks = churn.scoreAll();
        return new Snapshot(
                memberships.countActiveMembers(today),
                attendance.present().size(),
                attendance.countByCheckInAtBetween(startToday, now.plusSeconds(1)),
                attendance.countByCheckInAtBetween(d30, now.plusSeconds(1)),
                attendance.countByCheckInAtBetween(d60, d30),
                invoices.paidTotalBetween(startToday, now.plusSeconds(1)),
                invoices.paidTotalBetween(d30, now.plusSeconds(1)),
                invoices.paidTotalBetween(d60, d30),
                expense30,
                members.countByCreatedAtAfter(d30),
                memberships.expiringBetween(today, today.plusDays(7)).size(),
                invoices.countByStatus(InvoiceStatus.UNPAID),
                leads.dueOn(today).size(),
                risks.stream().filter(r -> r.level() == ChurnService.Level.HIGH).count(),
                risks.stream().filter(r -> r.level() == ChurnService.Level.MEDIUM).count());
    }

    /** Full staff dashboard payload: KPIs plus chart series. */
    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now(clock);
        ZoneId zone = clock.getZone();
        Instant from30 = today.minusDays(29).atStartOfDay(zone).toInstant();

        Map<LocalDate, Long> visits = new LinkedHashMap<>();
        for (int i = 29; i >= 0; i--) {
            visits.put(today.minusDays(i), 0L);
        }
        for (Object[] r : attendance.dailyCounts(from30)) {
            LocalDate d = r[0] instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) r[0];
            visits.computeIfPresent(d, (k, v) -> ((Number) r[1]).longValue());
        }
        List<Map<String, Object>> visitSeries = new ArrayList<>();
        visits.forEach((d, c) -> visitSeries.add(Map.of("date", d.toString(), "count", c)));

        long[] hours = new long[24];
        for (Object[] r : attendance.hourlyDistribution(from30)) {
            hours[((Number) r[0]).intValue()] = ((Number) r[1]).longValue();
        }
        List<Map<String, Object>> hourly = new ArrayList<>();
        for (int h = 5; h <= 23; h++) {
            hourly.add(Map.of("hour", h, "count", hours[h]));
        }

        List<MembershipDto> expiring = memberships.expiringBetween(today, today.plusDays(7)).stream()
                .map(MembershipDto::of).toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpis", snapshot());
        out.put("visits", visitSeries);
        out.put("hourly", hourly);
        out.put("finance", accounting.series(today.minusDays(29), today));
        out.put("expiring", expiring);
        out.put("salesByPlan", accounting.salesByPlan(today.minusDays(29), today));
        return out;
    }
}
