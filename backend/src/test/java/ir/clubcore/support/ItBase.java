package ir.clubcore.support;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
public abstract class ItBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected FakeLlmClient llm;

    protected Api api;
    protected String admin;

    @BeforeEach
    void setUpApi() {
        api = new Api(mvc);
        admin = api.admin();
        llm.available = true;
        llm.calls.clear();
    }

    protected long createPlan(String name, int days, Integer sessions, long price, int freezeDays) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("durationDays", days);
        body.put("sessionLimit", sessions);
        body.put("price", price);
        body.put("maxFreezeDays", freezeDays);
        return api.post("/api/plans", admin, body).expect(201).id("$.id");
    }

    /** Creates a member with a password and returns {memberId, phone}. */
    protected Map<String, Object> createMember(String token, String password) {
        String phone = Api.phone();
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "عضو آزمایشی " + phone.substring(7));
        body.put("phone", phone);
        body.put("password", password);
        long id = api.post("/api/members", token, body).expect(201).id("$.id");
        return Map.of("id", id, "phone", phone);
    }

    /** Sells a plan to a member and pays the invoice in cash. Returns membership id. */
    protected long sellAndPay(String staffToken, long memberId, long planId) {
        var res = api.post("/api/memberships", staffToken, Map.of("memberId", memberId, "planId", planId))
                .expect(201);
        long invoiceId = res.id("$.invoiceId");
        api.post("/api/invoices/" + invoiceId + "/pay", staffToken, Map.of("method", "CASH")).expect(200);
        return res.id("$.membership.id");
    }
}
