package ir.clubcore.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import ir.clubcore.ai.ChurnModel;
import ir.clubcore.ai.ChurnModel.Features;

class ChurnModelTest {

    @Test
    void loyalFrequentMemberHasLowRisk() {
        var s = ChurnModel.score(new Features(1, 8, 14, 40, null, 400, 5, false));
        assertThat(s.risk()).isLessThan(20);
    }

    @Test
    void absentMemberWithExpiredPlanHasHighRiskAndReasons() {
        var s = ChurnModel.score(new Features(35, 0, 10, -10, null, 200, 1, false));
        assertThat(s.risk()).isGreaterThanOrEqualTo(65);
        assertThat(s.reasons()).anyMatch(r -> r.contains("مراجعه نکرده"))
                .anyMatch(r -> r.contains("منقضی"));
    }

    @Test
    void decliningFrequencyIncreasesRisk() {
        var steady = ChurnModel.score(new Features(3, 6, 12, 30, null, 300, 2, false));
        var declining = ChurnModel.score(new Features(3, 1, 12, 30, null, 300, 2, false));
        assertThat(declining.risk()).isGreaterThan(steady.risk());
        assertThat(declining.reasons()).anyMatch(r -> r.contains("کاهش"));
    }

    @Test
    void upcomingExpiryAndFewSessionsAreFlagged() {
        var s = ChurnModel.score(new Features(2, 4, 8, 3, 1, 300, 2, false));
        assertThat(s.reasons()).anyMatch(r -> r.contains("روز دیگر تمام"))
                .anyMatch(r -> r.contains("جلسه باقی"));
    }

    @Test
    void riskIsAlwaysWithinBounds() {
        var worst = ChurnModel.score(new Features(500, 0, 30, null, 0, 1, 1, true));
        var best = ChurnModel.score(new Features(0, 14, 28, 300, null, 2000, 10, false));
        assertThat(worst.risk()).isBetween(0, 100);
        assertThat(best.risk()).isBetween(0, 100);
    }
}
