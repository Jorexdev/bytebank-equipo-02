package dev.bytebank.fraud.rules;

import dev.bytebank.fraud.FraudProperties;
import dev.bytebank.fraud.check.FraudCheckRepository;
import dev.bytebank.fraud.check.Verdict;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FrequencyRuleTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final String IBAN = "ES7639990001000000000001";

    private final FraudCheckRepository checks = mock(FraudCheckRepository.class);
    private final FrequencyRule rule = new FrequencyRule(
            new FraudProperties(new BigDecimal("20000"), new BigDecimal("1000"), 5, Duration.ofMinutes(10)), checks);

    @Test
    void allowsTheFifthValidation() {
        when(checks.countByOriginIbanAndCreatedAtGreaterThanEqual(eq(IBAN), any())).thenReturn(4L);

        assertThat(rule.evaluate(context())).isEmpty();
    }

    @Test
    void blocksTheSixthValidation() {
        when(checks.countByOriginIbanAndCreatedAtGreaterThanEqual(eq(IBAN), any())).thenReturn(5L);

        var hit = rule.evaluate(context());

        assertThat(hit).isPresent();
        assertThat(hit.get().code()).isEqualTo(FrequencyRule.CODE);
        assertThat(hit.get().verdict()).isEqualTo(Verdict.BLOQUEADA);
    }

    @Test
    void countsOnlyTheLastTenMinutes() {
        when(checks.countByOriginIbanAndCreatedAtGreaterThanEqual(eq(IBAN), any())).thenReturn(0L);

        rule.evaluate(context());

        var since = ArgumentCaptor.forClass(Instant.class);
        verify(checks).countByOriginIbanAndCreatedAtGreaterThanEqual(eq(IBAN), since.capture());
        assertThat(since.getValue()).isEqualTo(NOW.minus(Duration.ofMinutes(10)));
    }

    private static FraudRule.Context context() {
        return new FraudRule.Context(IBAN, "ES9121000418450200051332", 2L, new BigDecimal("1"), NOW);
    }
}
