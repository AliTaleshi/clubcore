package ir.clubcore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import ir.clubcore.billing.gateway.GatewayRegistry;
import ir.clubcore.billing.gateway.GatewayType;
import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;
import ir.clubcore.config.OutboundHttp;
import ir.clubcore.setting.SettingService;

class GatewayRegistryTest {

    private static GatewayRegistry registry(boolean mockEnabled, String configured) {
        AppProperties props = new AppProperties("http://x", "Asia/Tehran", null, null, null,
                new AppProperties.Payment(mockEnabled, new AppProperties.Payment.Zarinpal("mid", true),
                        new AppProperties.Payment.Zibal("zibal")),
                null, new AppProperties.Http(5, 20), null);
        SettingService settings = mock(SettingService.class);
        when(settings.get(eq(SettingService.ACTIVE_GATEWAY), anyString())).thenReturn(configured);
        return new GatewayRegistry(props, new OutboundHttp(RestClient.builder(), props), settings);
    }

    @Test
    void mockGatewayDoesNotExistUnlessExplicitlyEnabled() {
        GatewayRegistry prod = registry(false, "MOCK");
        assertThat(prod.list()).extracting(GatewayRegistry.GatewayInfo::type)
                .containsExactlyInAnyOrder(GatewayType.ZARINPAL, GatewayType.ZIBAL);
        assertThatThrownBy(() -> prod.get(GatewayType.MOCK)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> prod.activate(GatewayType.MOCK)).isInstanceOf(BusinessException.class);
    }

    @Test
    void fallsBackToZarinpalWhenConfiguredGatewayIsUnavailable() {
        assertThat(registry(false, "MOCK").activeType()).isEqualTo(GatewayType.ZARINPAL);
        assertThat(registry(false, "GARBAGE").activeType()).isEqualTo(GatewayType.ZARINPAL);
        assertThat(registry(false, "ZIBAL").activeType()).isEqualTo(GatewayType.ZIBAL);
        assertThat(registry(true, "MOCK").activeType()).isEqualTo(GatewayType.MOCK);
    }
}
