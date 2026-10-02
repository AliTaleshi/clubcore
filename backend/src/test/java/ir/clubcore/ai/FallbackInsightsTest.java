package ir.clubcore.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ir.clubcore.dashboard.KpiService;

class FallbackInsightsTest {

    @Test
    void flagsRevenueDropExpiringAndChurn() {
        var k = new KpiService.Snapshot(50, 3, 20, 400, 500, 1_000_000, 50_000_000, 100_000_000, 20_000_000, 5, 7,
                2, 1, 4, 6);
        String text = AiService.localInsights(k);
        assertThat(text).contains("50٪ کاهش").contains("7 اشتراک").contains("4 عضو").contains("2 فاکتور");
    }

    @Test
    void stableBusinessGetsGenericAdvice() {
        var k = new KpiService.Snapshot(50, 3, 20, 400, 400, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0);
        assertThat(AiService.localInsights(k)).contains("پایدار");
    }

    @Test
    void workoutFallbackRespectsDaysAndGoal() {
        String plan = FallbackTexts.workoutPlan("افزایش حجم عضلانی", "مبتدی", 4);
        assertThat(plan).contains("(4 روز در هفته)").contains("روز 4").doesNotContain("روز 5")
                .contains("اسکوات");
    }
}
