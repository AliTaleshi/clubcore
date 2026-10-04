package ir.clubcore.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ir.clubcore.auth.AuthHousekeeping;
import ir.clubcore.membership.MembershipRepository;
import ir.clubcore.membership.MembershipStatus;
import ir.clubcore.support.Api;
import ir.clubcore.support.ItBase;

/** Regression tests for issues found in the backend review. */
class ReviewFixesIT extends ItBase {

    @Autowired
    MembershipRepository memberships;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthHousekeeping housekeeping;

    private static List<Integer> parallel(int n, Callable<Integer> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                calls.add(call);
            }
            List<Integer> out = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void concurrentScansNeverCreateDuplicateOpenVisits() throws Exception {
        long plan = createPlan("هم‌زمانی", 30, 20, 100_000, 0);
        var m = createMember(admin, null);
        long msId = sellAndPay(admin, (Long) m.get("id"), plan);
        String phone = (String) m.get("phone");

        List<Integer> codes = parallel(8, () -> api.post("/api/attendance/scan", admin,
                Map.of("method", "MANUAL", "value", phone)).status());
        assertThat(codes).allMatch(c -> c == 200);

        Integer open = jdbc.queryForObject(
                "select count(*) from attendances where member_id = ? and check_out_at is null", Integer.class,
                m.get("id"));
        Integer total = jdbc.queryForObject("select count(*) from attendances where member_id = ?", Integer.class,
                m.get("id"));
        // 8 serialized toggles = 4 complete visits, nothing left open, and each visit consumed exactly one session.
        assertThat(open).isZero();
        assertThat(total).isEqualTo(4);
        assertThat(memberships.findById(msId).orElseThrow().getSessionsUsed()).isEqualTo(4);
    }

    @Test
    void concurrentPaymentsOfOneInvoiceSettleItOnce() throws Exception {
        var m = createMember(admin, null);
        long invoice = api.post("/api/invoices", admin, Map.of("memberId", m.get("id"), "title", "مکمل",
                "amount", 500_000, "discount", 0)).expect(201).id("$.id");

        List<Integer> codes = parallel(6, () -> api.post("/api/invoices/" + invoice + "/pay", admin,
                Map.of("method", "CASH")).status());
        assertThat(codes).filteredOn(c -> c == 200).hasSize(1);
        assertThat(codes).filteredOn(c -> c == 409).hasSize(5);
        Integer entries = jdbc.queryForObject("""
                select count(*) from journal_entries e join payments p on e.source_type = 'PAYMENT' and e.source_id = p.id
                where p.invoice_id = ?""", Integer.class, invoice);
        assertThat(entries).isEqualTo(1);
    }

    @Test
    void concurrentRedemptionsCannotOverspendPoints() throws Exception {
        var m = createMember(admin, "Secret123");
        String token = api.login((String) m.get("phone"), "Secret123");
        long reward = api.post("/api/loyalty/rewards", admin, Map.of("title", "هم‌زمان", "pointsCost", 100,
                "type", "GIFT", "value", 0)).expect(201).id("$.id");
        api.post("/api/loyalty/adjust", admin, Map.of("memberId", m.get("id"), "points", 150, "note", "تست"))
                .expect(200);

        List<Integer> codes = parallel(5, () -> api.post("/api/loyalty/redeem", token, Map.of("rewardId", reward))
                .status());
        assertThat(codes).filteredOn(c -> c == 200).hasSize(1);
        assertThat(api.get("/api/loyalty/me", token).id("$.summary.balance")).isEqualTo(50);
    }

    // ------------------------------------------------------------------ membership rules

    @Test
    void renewalAfterExhaustedSessionsStartsToday() {
        long plan = createPlan("یک جلسه", 30, 1, 100_000, 0);
        var m = createMember(admin, null);
        long first = sellAndPay(admin, (Long) m.get("id"), plan);
        api.post("/api/attendance/check-in/" + m.get("id"), admin, null).expect(200);

        long second = sellAndPay(admin, (Long) m.get("id"), plan);
        assertThat(memberships.findById(second).orElseThrow().getStartDate()).isEqualTo(LocalDate.now());
        assertThat(memberships.findById(first).orElseThrow().getStatus()).isEqualTo(MembershipStatus.EXPIRED);
    }

    @Test
    void membershipThatHasNotStartedCannotBeFrozen() {
        long plan = createPlan("آینده", 30, null, 100_000, 10);
        var m = createMember(admin, null);
        var res = api.post("/api/memberships", admin, Map.of("memberId", m.get("id"), "planId", plan,
                "startDate", LocalDate.now().plusDays(5).toString())).expect(201);
        api.post("/api/invoices/" + res.id("$.invoiceId") + "/pay", admin, Map.of("method", "CASH")).expect(200);
        var error = api.post("/api/memberships/" + res.id("$.membership.id") + "/freeze", admin, null).expect(400);
        assertThat((String) error.json("$.detail")).contains("شروع نشده");
    }

    @Test
    void referralBonusIsNotEarnedFromFreeInvoices() {
        var referrer = createMember(admin, "Secret123");
        String referrerToken = api.login((String) referrer.get("phone"), "Secret123");
        String code = api.get("/api/me/member", referrerToken).json("$.member.referralCode");
        var body = new HashMap<String, Object>();
        body.put("fullName", "معرفی رایگان");
        body.put("phone", Api.phone());
        body.put("referralCode", code);
        long referred = api.post("/api/members", admin, body).expect(201).id("$.id");

        long free = api.post("/api/invoices", admin, Map.of("memberId", referred, "title", "هدیه", "amount", 1000,
                "discount", 1000)).expect(201).id("$.id");
        api.post("/api/invoices/" + free + "/pay", admin, Map.of("method", "CASH")).expect(200);
        assertThat(api.get("/api/loyalty/me", referrerToken).id("$.summary.balance")).isZero();

        long paid = api.post("/api/invoices", admin, Map.of("memberId", referred, "title", "جلسه", "amount", 50_000,
                "discount", 0)).expect(201).id("$.id");
        api.post("/api/invoices/" + paid + "/pay", admin, Map.of("method", "CASH")).expect(200);
        assertThat(api.get("/api/loyalty/me", referrerToken).id("$.summary.balance")).isEqualTo(200);
    }

    // ------------------------------------------------------------------ accounting

    @Test
    void trialBalanceShowsCumulativeBalanceForBalanceSheetAccounts() {
        List<Map<String, Object>> accounts = api.get("/api/accounting/accounts", admin).json("$");
        long cash = accountId(accounts, "1110");
        long capital = accountId(accounts, "3100");
        LocalDate longAgo = LocalDate.now().minusYears(2);
        api.post("/api/accounting/journal", admin, Map.of("entryDate", longAgo.toString(), "description", "آورده اولیه",
                "lines", List.of(Map.of("accountId", cash, "debit", 7_000_000, "credit", 0),
                        Map.of("accountId", capital, "debit", 0, "credit", 7_000_000)))).expect(201);

        String from = LocalDate.now().minusDays(10).toString();
        List<Map<String, Object>> tb = api.get("/api/accounting/reports/trial-balance?from=" + from, admin).json("$");
        Map<String, Object> cashRow = tb.stream().filter(r -> "1110".equals(r.get("code"))).findFirst().orElseThrow();
        long cashBalance = ((Number) cashRow.get("balance")).longValue();
        long cashPeriodNet = ((Number) cashRow.get("debit")).longValue() - ((Number) cashRow.get("credit")).longValue();
        assertThat(cashBalance - cashPeriodNet).isGreaterThanOrEqualTo(7_000_000);

        api.get("/api/accounting/reports/trial-balance?from=2026-10-01&to=2026-01-01", admin).expect(400);
    }

    // ------------------------------------------------------------------ input handling and access

    @Test
    void malformedRequestsGetClientErrorsNotServerErrors() {
        api.get("/api/auth/login", null).expect(405);
        api.get("/api/members?page=-1", admin).expect(200);
        api.get("/api/crm/leads?size=0", admin).expect(200);
        String longName = "ا".repeat(200);
        var res = api.post("/api/crm/leads", admin, Map.of("fullName", longName, "phone", Api.phone())).expect(400);
        assertThat((Map<String, Object>) res.json("$.errors")).containsKey("fullName");
        String persianPassword = "رمز".repeat(20);
        api.post("/api/auth/register", null, Map.of("fullName", "x", "phone", Api.phone(), "password", persianPassword))
                .expect(400);
    }

    @Test
    void missingPriceIsRejectedInsteadOfCreatingAFreePlan() {
        var body = new HashMap<String, Object>();
        body.put("name", "بدون قیمت");
        body.put("durationDays", 30);
        body.put("price", null);
        var res = api.post("/api/plans", admin, body).expect(400);
        assertThat((Map<String, Object>) res.json("$.errors")).containsKey("price");
    }

    @Test
    void settingsRejectInconsistentLoyaltyRulesAndGatewayChanges() {
        api.put("/api/settings", admin, Map.of("loyalty.goldDiscountPercent", "150")).expect(400);
        api.put("/api/settings", admin, Map.of("loyalty.silverThreshold", "5000")).expect(400);
        api.put("/api/settings", admin, Map.of("loyalty.silverDiscountPercent", "50")).expect(400);
        api.put("/api/settings", admin, Map.of("loyalty.tomanPerPoint", "0")).expect(400);
        api.put("/api/settings", admin, Map.of("payment.activeGateway", "MOCK")).expect(400);
        api.put("/api/settings", admin, Map.of("loyalty.checkinPoints", "۱۵")).expect(200);
        assertThat((String) api.get("/api/settings", admin).json("$['loyalty.checkinPoints']")).isEqualTo("15");
        api.put("/api/settings", admin, Map.of("loyalty.checkinPoints", "10")).expect(200);
    }

    @Test
    void leaderboardIsStaffOnly() {
        var m = createMember(admin, "Secret123");
        api.get("/api/loyalty/leaderboard", api.login((String) m.get("phone"), "Secret123")).expect(403);
        api.get("/api/loyalty/leaderboard", admin).expect(200);
    }

    @Test
    void deactivationAndPasswordChangesRevokeSessions() {
        String phone = Api.phone();
        long id = api.post("/api/users", admin, Map.of("fullName", "موقت", "phone", phone, "role", "RECEPTIONIST",
                "password", "Passw0rd!")).expect(201).id("$.id");
        String refresh = api.post("/api/auth/login", null, Map.of("phone", phone, "password", "Passw0rd!"))
                .json("$.refreshToken");
        api.put("/api/users/" + id, admin, Map.of("fullName", "موقت", "phone", phone, "role", "RECEPTIONIST",
                "active", false)).expect(200);
        api.post("/api/auth/refresh", null, Map.of("refreshToken", refresh)).expect(401);

        var m = createMember(admin, "Secret123");
        var login = api.post("/api/auth/login", null, Map.of("phone", m.get("phone"), "password", "Secret123"));
        api.post("/api/auth/change-password", (String) login.json("$.accessToken"),
                Map.of("currentPassword", "Secret123", "newPassword", "NewSecret1")).expect(200);
        api.post("/api/auth/refresh", null, Map.of("refreshToken", login.json("$.refreshToken"))).expect(401);
    }

    @Test
    void housekeepingPurgesRevokedRefreshTokens() {
        var m = createMember(admin, "Secret123");
        String refresh = api.post("/api/auth/login", null, Map.of("phone", m.get("phone"), "password", "Secret123"))
                .json("$.refreshToken");
        api.post("/api/auth/logout", null, Map.of("refreshToken", refresh)).expect(200);
        assertThat(housekeeping.purge()).isPositive();
        Integer revoked = jdbc.queryForObject("select count(*) from refresh_tokens where revoked", Integer.class);
        assertThat(revoked).isZero();
    }

    private static long accountId(List<Map<String, Object>> accounts, String code) {
        return accounts.stream().filter(a -> code.equals(a.get("code"))).findFirst()
                .map(a -> ((Number) a.get("id")).longValue()).orElseThrow();
    }
}
