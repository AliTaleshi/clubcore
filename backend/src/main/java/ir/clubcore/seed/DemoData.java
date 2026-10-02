package ir.clubcore.seed;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ir.clubcore.accounting.Account;
import ir.clubcore.accounting.AccountRepository;
import ir.clubcore.accounting.AccountingService;
import ir.clubcore.accounting.JournalEntryRepository;
import ir.clubcore.attendance.Attendance;
import ir.clubcore.attendance.AttendanceRepository;
import ir.clubcore.attendance.EntryMethod;
import ir.clubcore.billing.InvoiceService;
import ir.clubcore.billing.Payment;
import ir.clubcore.billing.PaymentMethod;
import ir.clubcore.crm.CrmService;
import ir.clubcore.crm.LeadStatus;
import ir.clubcore.loyalty.LoyaltyReason;
import ir.clubcore.loyalty.LoyaltyTransaction;
import ir.clubcore.loyalty.LoyaltyTransactionRepository;
import ir.clubcore.member.Gender;
import ir.clubcore.member.Member;
import ir.clubcore.member.MemberRequest;
import ir.clubcore.member.MemberService;
import ir.clubcore.membership.Membership;
import ir.clubcore.membership.MembershipRepository;
import ir.clubcore.membership.MembershipService;
import ir.clubcore.membership.MembershipStatus;
import ir.clubcore.plan.Plan;
import ir.clubcore.plan.PlanRepository;
import ir.clubcore.user.Role;
import ir.clubcore.user.User;
import ir.clubcore.user.UserService;

/**
 * Realistic demo data: staff, plans, ~40 members with backdated memberships, visits and payments, leads and expenses.
 * Members follow different behaviour profiles (engaged, declining, lapsed, new) so churn scoring has signal.
 */
@Component
public class DemoData {

    static final String STAFF_PASSWORD = "Staff@12345";
    static final String MEMBER_PASSWORD = "Member@12345";

    private static final String[] FIRST = {"علی", "مریم", "رضا", "زهرا", "حسین", "فاطمه", "محمد", "سارا", "امیر",
            "نگار", "مهدی", "الهام", "سعید", "نازنین", "پویا", "لیلا", "حامد", "شیما", "کیان", "مینا"};
    private static final String[] LAST = {"محمدی", "حسینی", "رضایی", "کریمی", "احمدی", "موسوی", "جعفری", "صادقی",
            "رحیمی", "قاسمی", "نوری", "کاظمی", "اکبری", "شریفی", "یوسفی"};
    private static final String[] GOALS = {"کاهش وزن", "افزایش حجم عضلانی", "آمادگی جسمانی عمومی", "افزایش قدرت",
            "تناسب اندام"};

    private enum Profile {
        ENGAGED, DECLINING, LAPSED, NEW, EXPIRING
    }

    private final UserService users;
    private final MemberService members;
    private final PlanRepository plans;
    private final MembershipService membershipService;
    private final MembershipRepository memberships;
    private final InvoiceService invoices;
    private final AttendanceRepository attendance;
    private final LoyaltyTransactionRepository loyaltyTx;
    private final JournalEntryRepository journal;
    private final AccountingService accounting;
    private final AccountRepository accounts;
    private final CrmService crm;
    private final Clock clock;

    public DemoData(UserService users, MemberService members, PlanRepository plans,
            MembershipService membershipService, MembershipRepository memberships, InvoiceService invoices,
            AttendanceRepository attendance, LoyaltyTransactionRepository loyaltyTx, JournalEntryRepository journal,
            AccountingService accounting, AccountRepository accounts, CrmService crm, Clock clock) {
        this.users = users;
        this.members = members;
        this.plans = plans;
        this.membershipService = membershipService;
        this.memberships = memberships;
        this.invoices = invoices;
        this.attendance = attendance;
        this.loyaltyTx = loyaltyTx;
        this.journal = journal;
        this.accounting = accounting;
        this.accounts = accounts;
        this.crm = crm;
        this.clock = clock;
    }

    @Transactional
    public void load() {
        Random rnd = new Random(42);
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);

        User reception = users.create("09120000001", "سمیرا پذیرش", Role.RECEPTIONIST, STAFF_PASSWORD);
        users.create("09120000002", "بهروز حسابدار", Role.ACCOUNTANT, STAFF_PASSWORD);
        User coach1 = users.create("09120000003", "مربی آرش کمالی", Role.COACH, STAFF_PASSWORD);
        User coach2 = users.create("09120000004", "مربی نیلوفر راد", Role.COACH, STAFF_PASSWORD);

        Plan monthly = plan("یک ماهه نامحدود", 30, null, 1_500_000, 7);
        Plan twelve = plan("۱۲ جلسه‌ای", 45, 12, 1_100_000, 0);
        Plan quarterly = plan("سه ماهه نامحدود", 90, null, 3_900_000, 15);
        plan("شش ماهه ویژه", 180, null, 7_200_000, 30);
        List<Plan> sellable = List.of(monthly, twelve, quarterly);

        Profile[] profiles = Profile.values();
        for (int i = 0; i < 40; i++) {
            Profile profile = profiles[i % profiles.length];
            String name = FIRST[i % FIRST.length] + " " + LAST[(i * 7) % LAST.length];
            String phone = String.format("0912100%04d", i + 1);
            Gender gender = i % 2 == 0 ? Gender.MALE : Gender.FEMALE;
            Member m = members.create(new MemberRequest(name, phone, i == 0 ? MEMBER_PASSWORD : null, null, gender,
                    today.minusYears(18 + rnd.nextInt(30)).minusDays(rnd.nextInt(365)), null, null,
                    i % 3 == 0 ? coach1.getId() : i % 3 == 1 ? coach2.getId() : null,
                    String.format("CARD%05d", i + 1), null, GOALS[i % GOALS.length], null, null));

            Plan plan = sellable.get(rnd.nextInt(sellable.size()));
            LocalDate latestStart = switch (profile) {
                case ENGAGED, DECLINING -> today.minusDays(rnd.nextInt(Math.max(1, plan.getDurationDays() - 10)));
                case EXPIRING -> today.minusDays(plan.getDurationDays() - 1 - rnd.nextInt(5));
                case LAPSED -> today.minusDays(plan.getDurationDays() + 5 + rnd.nextInt(20));
                case NEW -> today.minusDays(10 + rnd.nextInt(20));
            };
            // Earlier renewals are laid out back-to-back before the latest membership.
            int renewals = profile == Profile.NEW ? 0 : rnd.nextInt(3);
            LocalDate firstStart = latestStart.minusDays((long) renewals * plan.getDurationDays());
            m.setCreatedAt(firstStart.atTime(10, 0).atZone(zone).toInstant());
            for (int r = renewals; r >= 0; r--) {
                LocalDate start = latestStart.minusDays((long) r * plan.getDurationDays());
                Membership ms = buyBackdated(m, plan, start, reception.getId(),
                        rnd.nextBoolean() ? PaymentMethod.CASH : PaymentMethod.POS, zone);
                simulateVisits(m, ms, profile, rnd, zone, today);
            }
        }
        membershipService.runDailyMaintenance();

        // Leads
        String[][] leadData = {{"کامران نیک‌نام", "09351110001", "اینستاگرام", "NEW"},
                {"هستی پارسا", "09351110002", "معرفی دوستان", "CONTACTED"},
                {"بهنام توکلی", "09351110003", "گوگل", "TRIAL"}, {"رویا امینی", "09351110004", "بنر محیطی", "LOST"},
                {"یاسر فرهادی", "09351110005", "اینستاگرام", "CONTACTED"},
                {"ترانه مقدم", "09351110006", "حضوری", "NEW"}, {"آرمان زارع", "09351110007", "اینستاگرام", "TRIAL"}};
        for (int i = 0; i < leadData.length; i++) {
            String[] l = leadData[i];
            crm.create(new CrmService.LeadRequest(l[0], l[1], l[2], LeadStatus.valueOf(l[3]), "عضویت ماهانه",
                    reception.getId(), today.plusDays(i % 3 - 1), null), reception.getId());
        }

        // Monthly expenses for the last three months
        Long rent = accounts.findByCode("5200").orElseThrow().getId();
        Long salary = accounts.findByCode("5100").orElseThrow().getId();
        Long utility = accounts.findByCode("5300").orElseThrow().getId();
        Long marketing = accounts.findByCode("5500").orElseThrow().getId();
        Long bank = accounts.findByCode(Account.BANK).orElseThrow().getId();
        Long cash = accounts.findByCode(Account.CASH).orElseThrow().getId();
        for (int month = 2; month >= 0; month--) {
            LocalDate d = today.minusDays(month * 30L + 2);
            accounting.addExpense(rent, bank, 12_000_000, d, "اجاره ماهانه سالن", null);
            accounting.addExpense(salary, bank, 15_000_000, d, "حقوق مربیان و پرسنل", null);
            accounting.addExpense(utility, cash, 2_500_000 + month * 200_000L, d.plusDays(5), "قبوض آب و برق و گاز", null);
            accounting.addExpense(marketing, bank, 1_500_000, d.plusDays(10), "تبلیغات اینستاگرام", null);
        }
    }

    private Plan plan(String name, int days, Integer sessions, long price, int freeze) {
        Plan p = new Plan();
        p.setName(name);
        p.setDurationDays(days);
        p.setSessionLimit(sessions);
        p.setPrice(price);
        p.setMaxFreezeDays(freeze);
        p.setDescription(sessions == null ? "ورود نامحدود در مدت اعتبار" : sessions + " جلسه در مدت اعتبار");
        return plans.save(p);
    }

    private Membership buyBackdated(Member m, Plan plan, LocalDate start, Long staffId, PaymentMethod method,
            ZoneId zone) {
        var result = membershipService.purchase(m, plan.getId(), null, null);
        Payment p = invoices.payOffline(result.invoiceId(), method, staffId);
        Membership ms = memberships.findById(result.membership().id()).orElseThrow();
        ms.setStartDate(start);
        ms.setEndDate(start.plusDays(plan.getDurationDays() - 1L));
        Instant paidAt = start.atTime(11, 0).atZone(zone).toInstant();
        ms.setCreatedAt(paidAt);
        ms.getInvoice().setCreatedAt(paidAt);
        ms.getInvoice().setPaidAt(paidAt);
        p.setCreatedAt(paidAt);
        p.setPaidAt(paidAt);
        journal.findFirstBySourceTypeAndSourceId("PAYMENT", p.getId()).ifPresent(e -> e.setEntryDate(start));
        loyaltyTx.findTop50ByMemberIdOrderByIdDesc(m.getId()).stream()
                .filter(t -> t.getReason() == LoyaltyReason.PURCHASE && t.getReference().endsWith(ms.getInvoice().getNumber()))
                .forEach(t -> t.setCreatedAt(paidAt));
        return ms;
    }

    private void simulateVisits(Member m, Membership ms, Profile profile, Random rnd, ZoneId zone, LocalDate today) {
        LocalDate end = ms.getEndDate().isBefore(today) ? ms.getEndDate() : today.minusDays(1);
        double baseRate = switch (profile) {
            case ENGAGED -> 0.65;
            case DECLINING -> 0.55;
            case LAPSED -> 0.35;
            case NEW -> 0.45;
            case EXPIRING -> 0.5;
        };
        for (LocalDate d = ms.getStartDate(); !d.isAfter(end); d = d.plusDays(1)) {
            double rate = baseRate;
            if (profile == Profile.DECLINING && d.isAfter(today.minusDays(21))) {
                rate = 0.04;
            }
            if (rnd.nextDouble() > rate || !ms.hasSessionsLeft()) {
                continue;
            }
            int hour = rnd.nextDouble() < 0.6 ? 17 + rnd.nextInt(5) : 7 + rnd.nextInt(5);
            Instant in = d.atTime(LocalTime.of(hour, rnd.nextInt(60))).atZone(zone).toInstant();
            Attendance a = new Attendance();
            a.setMember(m);
            a.setMembership(ms);
            a.setCheckInAt(in);
            a.setCheckOutAt(in.plusSeconds(3600 + rnd.nextInt(3600)));
            a.setMethod(EntryMethod.values()[rnd.nextInt(3)]);
            attendance.save(a);
            if (ms.getSessionsTotal() != null) {
                ms.setSessionsUsed(ms.getSessionsUsed() + 1);
            }
            LoyaltyTransaction t = new LoyaltyTransaction();
            t.setMemberId(m.getId());
            t.setPoints(10);
            t.setReason(LoyaltyReason.CHECKIN);
            t.setReference("CHECKIN:" + d);
            t.setCreatedAt(in);
            loyaltyTx.save(t);
        }
        if (ms.getEndDate().isBefore(today)) {
            ms.setStatus(MembershipStatus.EXPIRED);
        }
    }
}
