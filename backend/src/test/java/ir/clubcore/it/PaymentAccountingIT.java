package ir.clubcore.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ir.clubcore.support.ItBase;

class PaymentAccountingIT extends ItBase {

    private long balance(String code) {
        List<Map<String, Object>> rows = api.get("/api/accounting/reports/trial-balance?from=2000-01-01", admin)
                .expect(200).json("$");
        return rows.stream().filter(r -> code.equals(r.get("code")))
                .mapToLong(r -> ((Number) r.get("balance")).longValue()).sum();
    }

    @Test
    void onlinePaymentWithMockGatewayActivatesMembershipAndPostsJournal() {
        api.put("/api/payments/gateways/active", admin, Map.of("gateway", "MOCK")).expect(200);
        long plan = createPlan("آنلاین", 30, null, 750_000, 0);
        var m = createMember(admin, "Secret123");
        String token = api.login((String) m.get("phone"), "Secret123");
        long gatewayBefore = balance("1130");
        long revenueBefore = balance("4100");

        var quote = api.get("/api/me/memberships/quote?planId=" + plan, token).expect(200);
        assertThat(quote.id("$.total")).isEqualTo(750_000);
        long invoice = api.post("/api/me/memberships", token, Map.of("planId", plan)).expect(201).id("$.invoiceId");
        var start = api.post("/api/payments/online", token, Map.of("invoiceId", invoice)).expect(200);
        String url = start.json("$.redirectUrl");
        assertThat(url).startsWith("http://test.local/mock-gateway?authority=MOCK-");
        String authority = url.split("authority=")[1].split("&")[0];

        var cb = api.get("/api/payments/callback/mock?authority=" + authority + "&status=OK", null);
        assertThat(cb.status()).isEqualTo(302);
        long paymentId = start.id("$.paymentId");
        assertThat((String) api.get("/api/payments/" + paymentId, token).json("$.status")).isEqualTo("PAID");
        assertThat((String) api.get("/api/me/memberships", token).json("$[0].status")).isEqualTo("ACTIVE");
        assertThat(balance("1130") - gatewayBefore).isEqualTo(750_000);
        assertThat(balance("4100") - revenueBefore).isEqualTo(750_000);

        // Replaying the callback is harmless.
        api.get("/api/payments/callback/mock?authority=" + authority + "&status=OK", null);
        assertThat(balance("1130") - gatewayBefore).isEqualTo(750_000);
    }

    @Test
    void cancelledGatewayPaymentLeavesInvoiceUnpaid() {
        long plan = createPlan("لغو آنلاین", 30, null, 500_000, 0);
        var m = createMember(admin, "Secret123");
        String token = api.login((String) m.get("phone"), "Secret123");
        long invoice = api.post("/api/me/memberships", token, Map.of("planId", plan)).expect(201).id("$.invoiceId");
        var start = api.post("/api/payments/online", token, Map.of("invoiceId", invoice)).expect(200);
        String authority = ((String) start.json("$.redirectUrl")).split("authority=")[1].split("&")[0];
        api.get("/api/payments/callback/mock?authority=" + authority + "&status=NOK", null);
        assertThat((String) api.get("/api/payments/" + start.id("$.paymentId"), token).json("$.status"))
                .isEqualTo("FAILED");
        assertThat((String) api.get("/api/invoices/" + invoice, token).json("$.invoice.status")).isEqualTo("UNPAID");
    }

    @Test
    void memberCannotPaySomeoneElsesInvoice() {
        long plan = createPlan("دیگری", 30, null, 500_000, 0);
        var owner = createMember(admin, null);
        long invoice = api.post("/api/memberships", admin, Map.of("memberId", owner.get("id"), "planId", plan))
                .expect(201).id("$.invoiceId");
        var intruder = createMember(admin, "Secret123");
        String token = api.login((String) intruder.get("phone"), "Secret123");
        api.post("/api/payments/online", token, Map.of("invoiceId", invoice)).expect(403);
    }

    @Test
    void cashPaymentDebitsCashAndBooksRevenue() {
        long cashBefore = balance("1110");
        long otherBefore = balance("4900");
        var m = createMember(admin, null);
        long invoice = api.post("/api/invoices", admin,
                Map.of("memberId", m.get("id"), "title", "جلسه مربی خصوصی", "amount", 400_000, "discount", 50_000))
                .expect(201).id("$.id");
        api.post("/api/invoices/" + invoice + "/pay", admin, Map.of("method", "CASH")).expect(200);
        assertThat(balance("1110") - cashBefore).isEqualTo(350_000);
        assertThat(balance("4900") - otherBefore).isEqualTo(350_000);
    }

    @Test
    void expensesAndManualEntriesStayBalanced() {
        String accountant = api.staff("ACCOUNTANT");
        List<Map<String, Object>> accounts = api.get("/api/accounting/accounts", accountant).json("$");
        long rent = id(accounts, "5200");
        long bank = id(accounts, "1120");
        long cash = id(accounts, "1110");
        long capital = id(accounts, "3100");

        api.post("/api/accounting/expenses", accountant, Map.of("accountId", rent, "paidFromAccountId", bank,
                "amount", 9_000_000, "description", "اجاره")).expect(201);
        // Expense must use an expense account and be paid from an asset.
        api.post("/api/accounting/expenses", accountant, Map.of("accountId", bank, "paidFromAccountId", rent,
                "amount", 1, "description", "x")).expect(400);

        api.post("/api/accounting/journal", accountant, Map.of("description", "آورده نقدی", "lines", List.of(
                Map.of("accountId", cash, "debit", 1_000_000, "credit", 0),
                Map.of("accountId", capital, "debit", 0, "credit", 1_000_000)))).expect(201);
        api.post("/api/accounting/journal", accountant, Map.of("description", "نامتوازن", "lines", List.of(
                Map.of("accountId", cash, "debit", 1_000, "credit", 0),
                Map.of("accountId", capital, "debit", 0, "credit", 900)))).expect(400);

        List<Map<String, Object>> tb = api.get("/api/accounting/reports/trial-balance?from=2000-01-01", accountant)
                .json("$");
        long debit = tb.stream().mapToLong(r -> ((Number) r.get("debit")).longValue()).sum();
        long credit = tb.stream().mapToLong(r -> ((Number) r.get("credit")).longValue()).sum();
        assertThat(debit).isEqualTo(credit);

        var is = api.get("/api/accounting/reports/income-statement", accountant).expect(200);
        assertThat(is.id("$.netProfit")).isEqualTo(is.id("$.totalIncome") - is.id("$.totalExpense"));
        List<Object> series = api.get("/api/accounting/reports/series", accountant).json("$");
        assertThat(series).hasSize(30);
        List<Map<String, Object>> ledger = api.get("/api/accounting/reports/ledger/" + rent + "?from=2000-01-01",
                accountant).json("$");
        assertThat(ledger).isNotEmpty();
    }

    @Test
    void gatewaySelectionIsAdminOnly() {
        String reception = api.staff("RECEPTIONIST");
        api.put("/api/payments/gateways/active", reception, Map.of("gateway", "ZIBAL")).expect(403);
        api.put("/api/payments/gateways/active", admin, Map.of("gateway", "ZIBAL")).expect(200);
        List<Map<String, Object>> list = api.get("/api/payments/gateways", reception).json("$");
        assertThat(list).anyMatch(g -> "ZIBAL".equals(g.get("type")) && Boolean.TRUE.equals(g.get("active")));
        api.put("/api/payments/gateways/active", admin, Map.of("gateway", "MOCK")).expect(200);
    }

    private static long id(List<Map<String, Object>> accounts, String code) {
        return accounts.stream().filter(a -> code.equals(a.get("code"))).findFirst()
                .map(a -> ((Number) a.get("id")).longValue()).orElseThrow();
    }
}
