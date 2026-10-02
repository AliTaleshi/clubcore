package ir.clubcore.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ir.clubcore.ai.LlmClient;
import ir.clubcore.support.Api;
import ir.clubcore.support.ItBase;

class LoyaltyCrmAiIT extends ItBase {

    @Test
    void checkInAndPurchaseEarnPointsAndReferrerGetsBonusOnce() {
        long plan = createPlan("امتیازی", 30, null, 1_000_000, 0);
        var referrer = createMember(admin, "Secret123");
        String referrerToken = api.login((String) referrer.get("phone"), "Secret123");
        String code = api.get("/api/me/member", referrerToken).json("$.member.referralCode");

        var body = new HashMap<String, Object>();
        body.put("fullName", "معرفی‌شده");
        body.put("phone", Api.phone());
        body.put("referralCode", code);
        long referred = api.post("/api/members", admin, body).expect(201).id("$.id");

        sellAndPay(admin, referred, plan);
        sellAndPay(admin, referred, plan);
        api.post("/api/attendance/check-in/" + referred, admin, null).expect(200);

        var referredLoyalty = api.get("/api/loyalty/members/" + referred, admin).expect(200);
        // 2 × (1,000,000 / 10,000) purchase points + 10 check-in points.
        assertThat(referredLoyalty.id("$.summary.balance")).isEqualTo(210);
        var referrerLoyalty = api.get("/api/loyalty/me", referrerToken).expect(200);
        assertThat(referrerLoyalty.id("$.summary.balance")).isEqualTo(200);
        assertThat(referrerLoyalty.id("$.summary.referrals")).isEqualTo(1);
    }

    @Test
    void redeemDiscountRewardAndApplyItToPurchase() {
        long plan = createPlan("تخفیفی", 30, null, 1_000_000, 0);
        var m = createMember(admin, "Secret123");
        long memberId = (Long) m.get("id");
        String token = api.login((String) m.get("phone"), "Secret123");

        long reward = api.post("/api/loyalty/rewards", admin, Map.of("title", "۲۰٪ تخفیف", "pointsCost", 100,
                "type", "DISCOUNT_PERCENT", "value", 20)).expect(201).id("$.id");
        api.post("/api/loyalty/redeem", token, Map.of("rewardId", reward)).expect(400);

        api.post("/api/loyalty/adjust", admin, Map.of("memberId", memberId, "points", 150, "note", "هدیه")).expect(200);
        String code = api.post("/api/loyalty/redeem", token, Map.of("rewardId", reward)).expect(200).json("$.code");
        assertThat(api.get("/api/loyalty/me", token).id("$.summary.balance")).isEqualTo(50);

        var quote = api.get("/api/me/memberships/quote?planId=" + plan + "&discountCode=" + code, token).expect(200);
        assertThat(quote.id("$.codeDiscount")).isEqualTo(200_000);
        assertThat(quote.id("$.total")).isEqualTo(800_000);

        var purchase = api.post("/api/me/memberships", token, Map.of("planId", plan, "discountCode", code))
                .expect(201);
        assertThat(purchase.id("$.total")).isEqualTo(800_000);
        long invoice = purchase.id("$.invoiceId");
        // Code is reserved by the pending invoice; cancelling releases it.
        api.post("/api/invoices/" + invoice + "/cancel", admin, null).expect(200);
        var again = api.post("/api/me/memberships", token, Map.of("planId", plan, "discountCode", code)).expect(201);
        api.post("/api/invoices/" + again.id("$.invoiceId") + "/pay", admin, Map.of("method", "CASH")).expect(200);
        api.get("/api/me/memberships/quote?planId=" + plan + "&discountCode=" + code, token).expect(400);

        var other = createMember(admin, "Secret123");
        String otherToken = api.login((String) other.get("phone"), "Secret123");
        api.get("/api/me/memberships/quote?planId=" + plan + "&discountCode=" + code, otherToken).expect(400);
    }

    @Test
    void goldTierGetsAutomaticDiscount() {
        long plan = createPlan("طلایی", 30, null, 1_000_000, 0);
        var m = createMember(admin, null);
        long memberId = (Long) m.get("id");
        api.post("/api/loyalty/adjust", admin, Map.of("memberId", memberId, "points", 3000, "note", "تست")).expect(200);
        var quote = api.get("/api/memberships/quote?memberId=" + memberId + "&planId=" + plan, admin).expect(200);
        assertThat(quote.id("$.tierDiscountPercent")).isEqualTo(10);
        assertThat(quote.id("$.total")).isEqualTo(900_000);
    }

    @Test
    void leadLifecycleAndConversion() {
        String reception = api.staff("RECEPTIONIST");
        String phone = Api.phone();
        var lead = new HashMap<String, Object>();
        lead.put("fullName", "سرنخ تست");
        lead.put("phone", phone);
        lead.put("source", "اینستاگرام");
        lead.put("followUpDate", java.time.LocalDate.now().toString());
        long id = api.post("/api/crm/leads", reception, lead).expect(201).id("$.id");
        List<Integer> due = api.get("/api/crm/leads/due", reception).json("$[*].id");
        assertThat(due).contains((int) id);

        lead.put("status", "CONTACTED");
        api.put("/api/crm/leads/" + id, reception, lead).expect(200);
        api.post("/api/crm/activities", reception, Map.of("leadId", id, "type", "CALL", "content", "تماس گرفته شد"))
                .expect(201);
        List<Object> activities = api.get("/api/crm/leads/" + id, reception).json("$.activities");
        assertThat(activities).hasSizeGreaterThanOrEqualTo(3);

        long memberId = api.post("/api/crm/leads/" + id + "/convert", reception, null).expect(200).id("$.id");
        api.get("/api/members/" + memberId, reception).expect(200);
        api.post("/api/crm/leads/" + id + "/convert", reception, null).expect(409);
        assertThat(api.get("/api/crm/leads/stats", reception).id("$.byStatus.CONVERTED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void campaignSendsSmsToSegment() {
        long plan = createPlan("کمپین", 30, null, 100_000, 0);
        var m = createMember(admin, null);
        sellAndPay(admin, (Long) m.get("id"), plan);
        List<String> phones = api.get("/api/crm/segments/ALL_ACTIVE/members", admin).json("$[*].phone");
        assertThat(phones).contains((String) m.get("phone"));
        var c = api.post("/api/crm/campaigns", admin,
                Map.of("title", "جشنواره", "segment", "ALL_ACTIVE", "message", "{name} عزیز، به {gym} سر بزن"))
                .expect(201);
        assertThat(c.id("$.sentCount")).isEqualTo(phones.size());
        String reception = api.staff("RECEPTIONIST");
        api.post("/api/crm/campaigns", reception,
                Map.of("title", "x", "segment", "ALL_ACTIVE", "message", "x")).expect(403);
    }

    @Test
    void chatUsesLlmWithRoleContextAndKeepsHistory() {
        llm.answer = "سلام! پیشنهاد من سه جلسه تمرین در هفته است.";
        long plan = createPlan("چت", 30, null, 100_000, 0);
        var m = createMember(admin, "Secret123");
        sellAndPay(admin, (Long) m.get("id"), plan);
        String token = api.login((String) m.get("phone"), "Secret123");

        var reply = api.post("/api/ai/chat", token, Map.of("message", "چند جلسه در هفته تمرین کنم؟")).expect(200);
        assertThat((String) reply.json("$.source")).isEqualTo("claude");
        assertThat((String) reply.json("$.content")).isEqualTo(llm.answer);
        var call = llm.calls.get(llm.calls.size() - 1);
        assertThat(call.system()).contains("عضو باشگاه").contains("اشتراک فعال: چت");
        assertThat(call.turns()).last().extracting(LlmClient.Turn::text).isEqualTo("چند جلسه در هفته تمرین کنم؟");

        api.post("/api/ai/chat", token, Map.of("message", "ممنون")).expect(200);
        var second = llm.calls.get(llm.calls.size() - 1);
        assertThat(second.turns()).hasSize(3);
        assertThat(api.get("/api/ai/chat/history", token).<List<Object>>json("$")).hasSize(4);
        api.delete("/api/ai/chat/history", token).expect(200);
        assertThat(api.get("/api/ai/chat/history", token).<List<Object>>json("$")).isEmpty();
    }

    @Test
    void aiFallsBackToLocalWhenModelUnavailable() {
        llm.available = false;
        var reply = api.post("/api/ai/chat", admin, Map.of("message", "وضعیت باشگاه؟")).expect(200);
        assertThat((String) reply.json("$.source")).isEqualTo("local");
        assertThat((String) reply.json("$.content")).contains("اعضای فعال");

        var insights = api.get("/api/ai/insights", admin).expect(200);
        assertThat((String) insights.json("$.source")).isEqualTo("local");

        var plan = api.post("/api/ai/workout-plan", admin,
                Map.of("goal", "کاهش وزن", "level", "مبتدی", "daysPerWeek", 3)).expect(200);
        assertThat((String) plan.json("$.content")).contains("روز 3");
    }

    @Test
    void churnRadarAndRetentionMessage() {
        long plan = createPlan("ریزش", 30, null, 100_000, 0);
        var m = createMember(admin, null);
        long memberId = (Long) m.get("id");
        sellAndPay(admin, memberId, plan);
        List<Integer> scored = api.get("/api/ai/churn", admin).expect(200).json("$[*].memberId");
        assertThat(scored).contains((int) memberId);

        llm.answer = "دلمان برایت تنگ شده!";
        var msg = api.post("/api/ai/retention-message/" + memberId, admin, null).expect(200);
        assertThat((String) msg.json("$.message")).isEqualTo("دلمان برایت تنگ شده!");
        api.post("/api/ai/retention-message/" + memberId + "/send", admin, Map.of("message", "دلمان برایت تنگ شده!"))
                .expect(200);
        List<String> types = api.get("/api/crm/members/" + memberId + "/activities", admin).json("$[*].type");
        assertThat(types).contains("SMS");
    }

    @Test
    void coachSavesAiProgramForTrainee() {
        String coachPhone = Api.phone();
        long coachId = api.post("/api/users", admin,
                Map.of("fullName", "مربی", "phone", coachPhone, "role", "COACH", "password", "Passw0rd!"))
                .expect(201).id("$.id");
        String coach = api.login(coachPhone, "Passw0rd!");
        var m = createMember(admin, "Secret123");
        var body = new HashMap<String, Object>();
        body.put("fullName", "شاگرد");
        body.put("phone", m.get("phone"));
        body.put("coachId", coachId);
        api.put("/api/members/" + m.get("id"), admin, body).expect(200);

        llm.answer = "## برنامه\nروز ۱: اسکوات";
        String content = api.post("/api/ai/workout-plan", coach,
                Map.of("memberId", m.get("id"), "goal", "قدرت", "level", "متوسط", "daysPerWeek", 3)).expect(200)
                .json("$.content");
        api.post("/api/members/" + m.get("id") + "/programs", coach,
                Map.of("title", "برنامه قدرتی", "content", content, "aiGenerated", true)).expect(201);
        String memberToken = api.login((String) m.get("phone"), "Secret123");
        assertThat((String) api.get("/api/me/programs", memberToken).json("$[0].title")).isEqualTo("برنامه قدرتی");
    }

    @Test
    void dashboardAndSettings() {
        var d = api.get("/api/dashboard", admin).expect(200);
        assertThat(d.<List<Object>>json("$.visits")).hasSize(30);
        api.put("/api/settings", admin, Map.of("gym.name", "باشگاه تست")).expect(200);
        assertThat((String) api.get("/api/public/gym-info", null).json("$.name")).isEqualTo("باشگاه تست");
        api.put("/api/settings", admin, Map.of("unknown.key", "x")).expect(400);
        api.put("/api/settings", admin, Map.of("loyalty.checkinPoints", "-5")).expect(400);
    }
}
