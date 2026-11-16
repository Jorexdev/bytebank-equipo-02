package dev.bytebank.fraud.rules;

import dev.bytebank.fraud.FraudProperties;
import dev.bytebank.fraud.check.Verdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AmountRuleTest {

    private final AmountRule rule = new AmountRule(
            new FraudProperties(new BigDecimal("20000"), new BigDecimal("1000"), 5, Duration.ofMinutes(10)));

    @Test
    void blocksHugeAmounts() {
        var hit = rule.evaluate(context("25000"));

        assertThat(hit).isPresent();
        assertThat(hit.get().verdict()).isEqualTo(Verdict.BLOQUEADA);
    }

    @Test
    void blocksTheExactThreshold() {
        var hit = rule.evaluate(context("20000"));

        assertThat(hit).isPresent();
        assertThat(hit.get().verdict()).isEqualTo(Verdict.BLOQUEADA);
    }

    @Test
    void letsNormalAmountsThrough() {
        assertThat(rule.evaluate(context("150"))).isEmpty();
    }

    private static FraudRule.Context context(String amount) {
        return new FraudRule.Context("ES7639990001000000000001", "ES9121000418450200051332", 2L,
                new BigDecimal(amount), Instant.now());
    }
}
