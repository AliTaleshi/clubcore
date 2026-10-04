package ir.clubcore.billing.gateway;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ir.clubcore.common.BusinessException;
import ir.clubcore.config.AppProperties;
import ir.clubcore.config.OutboundHttp;
import ir.clubcore.setting.SettingService;

@Component
public class GatewayRegistry {

    private static final Logger log = LoggerFactory.getLogger(GatewayRegistry.class);

    public record GatewayInfo(GatewayType type, String title, boolean active) {
    }

    private final Map<GatewayType, PaymentGateway> gateways = new EnumMap<>(GatewayType.class);
    private final SettingService settings;

    public GatewayRegistry(AppProperties props, OutboundHttp http, SettingService settings) {
        this.settings = settings;
        register(new ZarinpalGateway(http.builder(), props.payment().zarinpal().merchantId(),
                props.payment().zarinpal().sandbox()));
        register(new ZibalGateway(http.builder(), props.payment().zibal().merchant()));
        // The mock gateway approves any payment, so it only exists when explicitly enabled.
        if (props.payment().mockEnabled()) {
            register(new MockGateway(props.publicUrl()));
        }
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

    /** The configured gateway, or Zarinpal when the configured one is unknown or disabled (e.g. MOCK in production). */
    public GatewayType activeType() {
        String configured = settings.get(SettingService.ACTIVE_GATEWAY, GatewayType.ZARINPAL.name());
        try {
            GatewayType type = GatewayType.valueOf(configured);
            if (gateways.containsKey(type)) {
                return type;
            }
        } catch (IllegalArgumentException e) {
            // fall through
        }
        log.warn("Configured payment gateway {} is not available; using ZARINPAL", configured);
        return GatewayType.ZARINPAL;
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
