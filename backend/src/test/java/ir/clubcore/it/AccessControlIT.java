package ir.clubcore.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import ir.clubcore.support.Api;
import ir.clubcore.support.ItBase;

class AccessControlIT extends ItBase {

    @Test
    void unauthenticatedRequestsGet401() {
        api.get("/api/members", null).expect(401);
        api.get("/api/dashboard", "not-a-jwt").expect(401);
        // Public endpoints stay open.
        api.get("/api/public/plans", null).expect(200);
        api.get("/api/public/gym-info", null).expect(200);
    }

    @Test
    void memberCannotReachStaffEndpoints() {
        var m = createMember(admin, "Secret123");
        String token = api.login((String) m.get("phone"), "Secret123");
        for (String url : List.of("/api/dashboard", "/api/members", "/api/users", "/api/accounting/accounts",
                "/api/crm/leads", "/api/invoices", "/api/ai/churn", "/api/ai/insights", "/api/settings")) {
            assertThat(api.get(url, token).status()).as(url).isEqualTo(403);
        }
        api.post("/api/plans", token, Map.of("name", "x", "durationDays", 1, "price", 1)).expect(403);
    }

    @Test
    void memberCannotSeeAnotherMembersData() {
        var a = createMember(admin, "Secret123");
        var b = createMember(admin, "Secret123");
        String tokenA = api.login((String) a.get("phone"), "Secret123");
        api.get("/api/members/" + b.get("id"), tokenA).expect(403);
        api.get("/api/members/" + b.get("id") + "/memberships", tokenA).expect(403);
        api.get("/api/members/" + a.get("id"), tokenA).expect(200);
    }

    @Test
    void receptionistCannotUseAccountingOrManageStaff() {
        String reception = api.staff("RECEPTIONIST");
        api.get("/api/accounting/accounts", reception).expect(403);
        api.get("/api/users", reception).expect(403);
        api.get("/api/ai/insights", reception).expect(403);
        api.get("/api/crm/leads", reception).expect(200);
        api.get("/api/dashboard", reception).expect(200);
    }

    @Test
    void accountantCannotUseCrmOrCheckIn() {
        String accountant = api.staff("ACCOUNTANT");
        api.get("/api/crm/leads", accountant).expect(403);
        api.post("/api/attendance/scan", accountant, Map.of("method", "MANUAL", "value", "x")).expect(403);
        api.get("/api/accounting/reports/trial-balance", accountant).expect(200);
    }

    @Test
    void coachOnlySeesOwnTrainees() {
        String coachPhone = Api.phone();
        long coachId = api.post("/api/users", admin,
                Map.of("fullName", "مربی", "phone", coachPhone, "role", "COACH", "password", "Passw0rd!"))
                .expect(201).id("$.id");
        String coach = api.login(coachPhone, "Passw0rd!");

        var mine = createMember(admin, "Secret123");
        var other = createMember(admin, "Secret123");
        var body = new java.util.HashMap<String, Object>();
        body.put("fullName", "شاگرد مربی");
        body.put("phone", mine.get("phone"));
        body.put("coachId", coachId);
        api.put("/api/members/" + mine.get("id"), admin, body).expect(200);

        api.get("/api/members/" + mine.get("id"), coach).expect(200);
        api.get("/api/members/" + other.get("id"), coach).expect(403);
        List<Integer> trainees = api.get("/api/coaching/trainees", coach).expect(200).json("$[*].id");
        assertThat(trainees).containsExactly(((Number) mine.get("id")).intValue());
        List<Integer> searched = api.get("/api/members?q=", coach).expect(200).json("$.content[*].id");
        assertThat(searched).containsExactly(((Number) mine.get("id")).intValue());
    }

    @Test
    void adminCannotDemoteThemselves() {
        long adminId = api.get("/api/auth/me", admin).id("$.id");
        api.put("/api/users/" + adminId, admin,
                Map.of("fullName", "مدیر", "phone", "09120000000", "role", "RECEPTIONIST")).expect(400);
    }

    @Test
    void inactiveStaffCannotLogIn() {
        String phone = Api.phone();
        long id = api.post("/api/users", admin,
                Map.of("fullName", "موقت", "phone", phone, "role", "RECEPTIONIST", "password", "Passw0rd!"))
                .expect(201).id("$.id");
        api.put("/api/users/" + id, admin,
                Map.of("fullName", "موقت", "phone", phone, "role", "RECEPTIONIST", "active", false)).expect(200);
        api.post("/api/auth/login", null, Map.of("phone", phone, "password", "Passw0rd!")).expect(403);
    }
}
