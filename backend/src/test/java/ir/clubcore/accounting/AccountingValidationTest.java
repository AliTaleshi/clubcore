package ir.clubcore.accounting;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import ir.clubcore.accounting.AccountingService.LineInput;
import ir.clubcore.common.BusinessException;

class AccountingValidationTest {

    @Test
    void acceptsBalancedEntry() {
        assertThatCode(() -> AccountingService.validate(List.of(new LineInput(1L, 500, 0), new LineInput(2L, 0, 300),
                new LineInput(3L, 0, 200)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnbalancedEntry() {
        assertThatThrownBy(() -> AccountingService.validate(
                List.of(new LineInput(1L, 500, 0), new LineInput(2L, 0, 400))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("برابر نیست");
    }

    @Test
    void rejectsSingleLineAndTwoSidedOrEmptyLines() {
        assertThatThrownBy(() -> AccountingService.validate(List.of(new LineInput(1L, 500, 0))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AccountingService.validate(
                List.of(new LineInput(1L, 500, 500), new LineInput(2L, 0, 0))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> AccountingService.validate(
                List.of(new LineInput(1L, -5, 0), new LineInput(2L, 0, -5))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void accountTypesHaveCorrectNormalBalance() {
        org.assertj.core.api.Assertions.assertThat(AccountType.ASSET.debitNormal()).isTrue();
        org.assertj.core.api.Assertions.assertThat(AccountType.EXPENSE.debitNormal()).isTrue();
        org.assertj.core.api.Assertions.assertThat(AccountType.INCOME.debitNormal()).isFalse();
        org.assertj.core.api.Assertions.assertThat(AccountType.LIABILITY.debitNormal()).isFalse();
    }
}
