package ir.clubcore.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import ir.clubcore.loyalty.LoyaltyService;
import ir.clubcore.loyalty.Tier;
import ir.clubcore.setting.SettingService;

class LoyaltyTierTest {

    private final LoyaltyService service;

    LoyaltyTierTest() {
        SettingService settings = mock(SettingService.class);
        when(settings.getLong(anyString(), anyLong())).thenAnswer(inv -> inv.getArgument(1));
        service = new LoyaltyService(null, null, null, null, null, settings, null, Clock.systemUTC());
    }

    @Test
    void bronzeBelowSilverThreshold() {
        var t = service.tierFor(999);
        assertThat(t.tier()).isEqualTo(Tier.BRONZE);
        assertThat(t.discountPercent()).isZero();
        assertThat(t.next()).isEqualTo(Tier.SILVER);
        assertThat(t.pointsToNext()).isEqualTo(1);
    }

    @Test
    void silverAndGoldThresholds() {
        assertThat(service.tierFor(1000).tier()).isEqualTo(Tier.SILVER);
        assertThat(service.tierFor(1000).discountPercent()).isEqualTo(5);
        assertThat(service.tierFor(2999).pointsToNext()).isEqualTo(1);
        var gold = service.tierFor(3000);
        assertThat(gold.tier()).isEqualTo(Tier.GOLD);
        assertThat(gold.discountPercent()).isEqualTo(10);
        assertThat(gold.next()).isNull();
    }
}
