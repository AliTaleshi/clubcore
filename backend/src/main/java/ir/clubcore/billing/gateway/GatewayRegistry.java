package ir.clubcore.billing.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;
import ir.clubcore.setting.SettingService;

@Component
public class GatewayRegistry {

    public record GatewayInfo(GatewayType type, String title, boolean active) {
    }

    private final Map<GatewayType, PaymentGateway> gateways = new EnumMap<>(GatewayType.class);
    private final SettingService settings;

    public GatewayRegistry(AppProperties props, RestClient.Builder builder, SettingService settings) {
        this.settings = settings;
        register(new ZarinpalGateway(builder.clone(), props.payment().zarinpal().merchantId(),
                props.payment().zarinpal().sandbox()));
        register(new ZibalGateway(builder.clone(), props.payment().zibal().merchant()));
        register(new MockGateway(props.publicUrl()));
    }

    /** Visible for tests that need to swap a gateway implementation. */
    public void register(PaymentGateway gateway) {
        gateways.put(gateway.type(), gateway);
    }

    public PaymentGateway get(GatewayType type) {
        PaymentGateway g = gateways.get(type);
        if (g == null) {
            throw new BusinessException("درگاه پرداخت پشتیبانی نمی‌شود");
        }
        return g;
    }

    public PaymentGateway active() {
        return get(activeType());
    }

    public GatewayType activeType() {
        try {
            return GatewayType.valueOf(settings.get(SettingService.ACTIVE_GATEWAY, "MOCK"));
        } catch (IllegalArgumentException e) {
            return GatewayType.MOCK;
        }
    }

    public List<GatewayInfo> list() {
        GatewayType active = activeType();
        return gateways.values().stream().map(g -> new GatewayInfo(g.type(), g.title(), g.type() == active)).toList();
    }

    public void activate(GatewayType type) {
        get(type);
        settings.set(SettingService.ACTIVE_GATEWAY, type.name());
    }
}
