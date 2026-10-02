package ir.clubcore.support;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/** Small fluent HTTP helper over MockMvc for readable integration tests. */
public class Api {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final AtomicInteger PHONE_SEQ = new AtomicInteger((int) (System.nanoTime() % 100000));

    public record Res(int status, String body) {
        public <T> T json(String path) {
            return JsonPath.read(body, path);
        }

        public long id(String path) {
            return ((Number) JsonPath.read(body, path)).longValue();
        }

        public Res expect(int expected) {
            if (status != expected) {
                throw new AssertionError("Expected HTTP " + expected + " but got " + status + ": " + body);
            }
            return this;
        }
    }

    private final MockMvc mvc;

    public Api(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Unique valid Iranian mobile number for test isolation. */
    public static String phone() {
        return String.format("0919%07d", PHONE_SEQ.incrementAndGet() % 10_000_000);
    }

    public static String json(Object o) {
        return JSON.writeValueAsString(o);
    }

    public Res get(String url, String token) {
        return exec(MockMvcRequestBuilders.get(url), token, null);
    }

    public Res post(String url, String token, Object body) {
        return exec(MockMvcRequestBuilders.post(url), token, body);
    }

    public Res put(String url, String token, Object body) {
        return exec(MockMvcRequestBuilders.put(url), token, body);
    }

    public Res delete(String url, String token) {
        return exec(MockMvcRequestBuilders.delete(url), token, null);
    }

    public String login(String phone, String password) {
        return post("/api/auth/login", null, Map.of("phone", phone, "password", password)).expect(200)
                .json("$.accessToken");
    }

    public String admin() {
        return login("09120000000", "Admin@12345");
    }

    /** Creates a staff user with the given role and returns their access token. */
    public String staff(String role) {
        String phone = phone();
        post("/api/users", admin(), Map.of("fullName", "کارمند " + role, "phone", phone, "role", role, "password",
                "Passw0rd!")).expect(201);
        return login(phone, "Passw0rd!");
    }

    private Res exec(MockHttpServletRequestBuilder req, String token, Object body) {
        try {
            if (token != null) {
                req.header("Authorization", "Bearer " + token);
            }
            if (body != null) {
                req.contentType(MediaType.APPLICATION_JSON).content(body instanceof String s ? s : json(body));
            }
            MvcResult r = mvc.perform(req).andReturn();
            return new Res(r.getResponse().getStatus(), r.getResponse().getContentAsString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
