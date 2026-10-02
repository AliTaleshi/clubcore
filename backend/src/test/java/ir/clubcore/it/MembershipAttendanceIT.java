package ir.clubcore.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ir.clubcore.membership.MembershipRepository;
import ir.clubcore.membership.MembershipService;
import ir.clubcore.membership.MembershipStatus;
import ir.clubcore.support.ItBase;

class MembershipAttendanceIT extends ItBase {

    @Autowired
    MembershipRepository memberships;

    @Autowired
    MembershipService membershipService;

    @Test
    void pendingMembershipBlocksEntryUntilPaid() {
        String reception = api.staff("RECEPTIONIST");
        long plan = createPlan("ماهانه تست", 30, null, 1_000_000, 0);
        var m = createMember(reception, null);
        String phone = (String) m.get("phone");

        var purchase = api.post("/api/memberships", reception, Map.of("memberId", m.get("id"), "planId", plan))
                .expect(201);
        assertThat((String) purchase.json("$.membership.status")).isEqualTo("PENDING_PAYMENT");
        assertThat(purchase.id("$.total")).isEqualTo(1_000_000);

        var blocked = api.post("/api/attendance/scan", reception, Map.of("method", "MANUAL", "value", phone))
                .expect(400);
        assertThat((String) blocked.json("$.detail")).contains("انتظار پرداخت");

        // Second purchase while one is pending is refused.
        api.post("/api/memberships", reception, Map.of("memberId", m.get("id"), "planId", plan)).expect(400);

        api.post("/api/invoices/" + purchase.id("$.invoiceId") + "/pay", reception, Map.of("method", "POS"))
                .expect(200);
        api.post("/api/invoices/" + purchase.id("$.invoiceId") + "/pay", reception, Map.of("method", "POS"))
                .expect(409);

        var in = api.post("/api/attendance/scan", reception, Map.of("method", "MANUAL", "value", phone)).expect(200);
        assertThat((String) in.json("$.action")).isEqualTo("CHECK_IN");
        List<String> present = api.get("/api/attendance/present", reception).json("$[*].memberName");
        assertThat(present).contains((String) in.json("$.memberName"));

        var out = api.post("/api/attendance/scan", reception, Map.of("method", "MANUAL", "value", phone)).expect(200);
        assertThat((String) out.json("$.action")).isEqualTo("CHECK_OUT");
    }

    @Test
    void sessionBasedPlanConsumesSessionsAndBlocksWhenExhausted() {
        long plan = createPlan("دو جلسه‌ای", 30, 2, 200_000, 0);
        var m = createMember(admin, null);
        long msId = sellAndPay(admin, (Long) m.get("id"), plan);
        String card = "CARD-" + m.get("id");
        var body = new HashMap<String, Object>();
        body.put("fullName", "کارت‌دار");
        body.put("phone", m.get("phone"));
        body.put("cardNo", card);
        api.put("/api/members/" + m.get("id"), admin, body).expect(200);

        for (int i = 0; i < 2; i++) {
            api.post("/api/attendance/scan", admin, Map.of("method", "CARD", "value", card)).expect(200);
            api.post("/api/attendance/scan", admin, Map.of("method", "CARD", "value", card)).expect(200);
        }
        assertThat(memberships.findById(msId).orElseThrow().getSessionsUsed()).isEqualTo(2);
        var denied = api.post("/api/attendance/scan", admin, Map.of("method", "CARD", "value", card)).expect(400);
        assertThat((String) denied.json("$.detail")).contains("جلسات");
        api.post("/api/attendance/scan", admin, Map.of("method", "CARD", "value", "UNKNOWN")).expect(400);
    }

    @Test
    void qrCheckInUsesShortLivedMemberToken() {
        long plan = createPlan("QR پلن", 30, null, 100_000, 0);
        var m = createMember(admin, "Secret123");
        sellAndPay(admin, (Long) m.get("id"), plan);
        String memberToken = api.login((String) m.get("phone"), "Secret123");
        String qr = api.get("/api/me/qr", memberToken).expect(200).json("$.token");

        // A QR token must never work as an API bearer token.
        api.get("/api/me/member", qr).expect(401);

        var res = api.post("/api/attendance/scan", admin, Map.of("method", "QR", "value", qr)).expect(200);
        assertThat((String) res.json("$.action")).isEqualTo("CHECK_IN");
        api.post("/api/attendance/scan", admin, Map.of("method", "QR", "value", qr + "x")).expect(400);
        assertThat(api.get("/api/me/attendance", memberToken).id("$.totalElements")).isEqualTo(1);
    }

    @Test
    void renewalStacksAfterCurrentMembership() {
        long plan = createPlan("سی روزه", 30, null, 300_000, 0);
        var m = createMember(admin, null);
        long first = sellAndPay(admin, (Long) m.get("id"), plan);
        long second = sellAndPay(admin, (Long) m.get("id"), plan);
        var a = memberships.findById(first).orElseThrow();
        var b = memberships.findById(second).orElseThrow();
        assertThat(b.getStartDate()).isEqualTo(a.getEndDate().plusDays(1));
        assertThat(b.getEndDate()).isEqualTo(b.getStartDate().plusDays(29));
    }

    @Test
    void freezeAndUnfreeze() {
        long plan = createPlan("قابل فریز", 30, null, 300_000, 10);
        long noFreeze = createPlan("بدون فریز", 30, null, 300_000, 0);
        var m = createMember(admin, null);
        long ms = sellAndPay(admin, (Long) m.get("id"), plan);
        api.post("/api/memberships/" + ms + "/freeze", admin, null).expect(200);
        var blocked = api.post("/api/attendance/scan", admin, Map.of("method", "MANUAL", "value", m.get("phone")))
                .expect(400);
        assertThat((String) blocked.json("$.detail")).contains("فریز");
        api.post("/api/memberships/" + ms + "/freeze", admin, null).expect(400);

        // Simulate 4 frozen days.
        var entity = memberships.findById(ms).orElseThrow();
        LocalDate originalEnd = entity.getEndDate();
        entity.setFrozenSince(LocalDate.now().minusDays(4));
        memberships.save(entity);
        var unfrozen = api.post("/api/memberships/" + ms + "/unfreeze", admin, null).expect(200);
        assertThat((String) unfrozen.json("$.endDate")).isEqualTo(originalEnd.plusDays(4).toString());
        assertThat(unfrozen.id("$.freezeDaysUsed")).isEqualTo(4);

        var m2 = createMember(admin, null);
        long ms2 = sellAndPay(admin, (Long) m2.get("id"), noFreeze);
        api.post("/api/memberships/" + ms2 + "/freeze", admin, null).expect(400);
    }

    @Test
    void nightlyJobExpiresFinishedMemberships() {
        long plan = createPlan("کوتاه", 5, null, 50_000, 0);
        var m = createMember(admin, null);
        long ms = sellAndPay(admin, (Long) m.get("id"), plan);
        var e = memberships.findById(ms).orElseThrow();
        e.setStartDate(LocalDate.now().minusDays(10));
        e.setEndDate(LocalDate.now().minusDays(6));
        memberships.save(e);
        membershipService.runDailyMaintenance();
        assertThat(memberships.findById(ms).orElseThrow().getStatus()).isEqualTo(MembershipStatus.EXPIRED);
        var blocked = api.post("/api/attendance/scan", admin, Map.of("method", "MANUAL", "value", m.get("phone")))
                .expect(400);
        assertThat((String) blocked.json("$.detail")).contains("منقضی");
    }

    @Test
    void cancellingPendingMembershipCancelsInvoice() {
        long plan = createPlan("لغو", 30, null, 100_000, 0);
        var m = createMember(admin, null);
        var purchase = api.post("/api/memberships", admin, Map.of("memberId", m.get("id"), "planId", plan))
                .expect(201);
        long ms = purchase.id("$.membership.id");
        api.post("/api/memberships/" + ms + "/cancel", admin, null).expect(200);
        assertThat(memberships.findById(ms).orElseThrow().getStatus()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat((String) api.get("/api/invoices/" + purchase.id("$.invoiceId"), admin).json("$.invoice.status"))
                .isEqualTo("CANCELLED");
        api.post("/api/invoices/" + purchase.id("$.invoiceId") + "/pay", admin, Map.of("method", "CASH")).expect(400);
    }

    @Test
    void inactivePlansCannotBeSold() {
        var body = new HashMap<String, Object>();
        body.put("name", "غیرفعال");
        body.put("durationDays", 30);
        body.put("price", 1000);
        body.put("maxFreezeDays", 0);
        body.put("active", false);
        long plan = api.post("/api/plans", admin, body).expect(201).id("$.id");
        var m = createMember(admin, null);
        api.post("/api/memberships", admin, Map.of("memberId", m.get("id"), "planId", plan)).expect(404);
        List<Integer> publicIds = api.get("/api/public/plans", null).json("$[*].id");
        assertThat(publicIds).doesNotContain((int) plan);
    }

    @Test
    void memberValidation() {
        var bad = new HashMap<String, Object>();
        bad.put("fullName", "کد ملی غلط");
        bad.put("phone", ir.clubcore.support.Api.phone());
        bad.put("nationalCode", "1234567890");
        api.post("/api/members", admin, bad).expect(400);
        bad.put("nationalCode", "0499370899");
        api.post("/api/members", admin, bad).expect(201);
        api.post("/api/members", admin, bad).expect(409);
    }
}
