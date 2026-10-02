package ir.clubcore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import ir.clubcore.billing.gateway.GatewayException;
import ir.clubcore.billing.gateway.MockGateway;
import ir.clubcore.billing.gateway.ZarinpalGateway;
import ir.clubcore.billing.gateway.ZibalGateway;

class GatewayClientsTest {

    @Test
    void zarinpalSandboxRequestAndVerify() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://sandbox.zarinpal.com/pg/v4/payment/request.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.merchant_id").value("mid"))
                .andExpect(jsonPath("$.amount").value(150000))
                .andExpect(jsonPath("$.currency").value("IRT"))
                .andExpect(jsonPath("$.metadata.mobile").value("09120000000"))
                .andRespond(withSuccess("{\"data\":{\"code\":100,\"authority\":\"A0000001\"},\"errors\":[]}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://sandbox.zarinpal.com/pg/v4/payment/verify.json"))
                .andExpect(jsonPath("$.authority").value("A0000001"))
                .andRespond(withSuccess("{\"data\":{\"code\":100,\"ref_id\":201,\"card_pan\":\"502229******5995\"}}",
                        MediaType.APPLICATION_JSON));

        ZarinpalGateway gw = new ZarinpalGateway(builder, "mid", true);
        var start = gw.request(150000, "desc", "09120000000", "http://cb");
        assertThat(start.authority()).isEqualTo("A0000001");
        assertThat(start.redirectUrl()).isEqualTo("https://sandbox.zarinpal.com/pg/StartPay/A0000001");
        assertThat(gw.authorityFrom(Map.of("Authority", "A0000001", "Status", "OK"))).isEqualTo("A0000001");
        assertThat(gw.callbackSuccessful(Map.of("Status", "OK"))).isTrue();
        assertThat(gw.callbackSuccessful(Map.of("Status", "NOK"))).isFalse();

        var verify = gw.verify("A0000001", 150000);
        assertThat(verify.success()).isTrue();
        assertThat(verify.refId()).isEqualTo("201");
        server.verify();
    }

    @Test
    void zarinpalRejectedVerificationFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://payment.zarinpal.com/pg/v4/payment/verify.json"))
                .andRespond(withSuccess("{\"data\":[],\"errors\":{\"code\":-51}}", MediaType.APPLICATION_JSON));
        var result = new ZarinpalGateway(builder, "mid", false).verify("A1", 1000);
        assertThat(result.success()).isFalse();
    }

    @Test
    void zarinpalNetworkErrorOnRequestRaisesGatewayException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://payment.zarinpal.com/pg/v4/payment/request.json"))
                .andRespond(withServerError());
        assertThatThrownBy(() -> new ZarinpalGateway(builder, "mid", false).request(1000, "d", null, "http://cb"))
                .isInstanceOf(GatewayException.class);
    }

    @Test
    void zibalConvertsTomanToRialAndChecksAmountOnVerify() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gateway.zibal.ir/v1/request"))
                .andExpect(jsonPath("$.merchant").value("zibal"))
                .andExpect(jsonPath("$.amount").value(1_500_000))
                .andRespond(withSuccess("{\"result\":100,\"trackId\":15966442233}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://gateway.zibal.ir/v1/verify"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.trackId").value(15966442233L))
                .andRespond(withSuccess("{\"result\":100,\"refNumber\":7777,\"cardNumber\":\"62741****44\",\"amount\":1500000}",
                        MediaType.APPLICATION_JSON));

        ZibalGateway gw = new ZibalGateway(builder, "zibal");
        var start = gw.request(150000, "desc", null, "http://cb");
        assertThat(start.redirectUrl()).isEqualTo("https://gateway.zibal.ir/start/15966442233");
        assertThat(gw.callbackSuccessful(Map.of("success", "1"))).isTrue();
        assertThat(gw.callbackSuccessful(Map.of("success", "0"))).isFalse();
        var v = gw.verify("15966442233", 150000);
        assertThat(v.success()).isTrue();
        assertThat(v.refId()).isEqualTo("7777");
        server.verify();
    }

    @Test
    void zibalAmountMismatchIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://gateway.zibal.ir/v1/verify"))
                .andRespond(withSuccess("{\"result\":100,\"refNumber\":1,\"amount\":10}", MediaType.APPLICATION_JSON));
        assertThat(new ZibalGateway(builder, "zibal").verify("123", 150000).success()).isFalse();
    }

    @Test
    void mockGatewayRedirectsToSpaAndVerifiesOwnAuthorities() {
        MockGateway gw = new MockGateway("http://spa");
        var start = gw.request(5000, "توضیح", null, "http://spa/api/payments/callback/mock");
        assertThat(start.authority()).startsWith("MOCK-");
        assertThat(start.redirectUrl()).startsWith("http://spa/mock-gateway?authority=" + start.authority());
        assertThat(gw.verify(start.authority(), 5000).success()).isTrue();
        assertThat(gw.verify("FOREIGN", 5000).success()).isFalse();
    }
}
