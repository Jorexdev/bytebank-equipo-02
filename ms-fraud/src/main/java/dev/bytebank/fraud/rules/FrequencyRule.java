package dev.bytebank.fraud.rules;

import dev.bytebank.fraud.FraudProperties;
import dev.bytebank.fraud.check.FraudCheckRepository;
import dev.bytebank.fraud.check.Verdict;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** Blocks the account after too many validations inside the configured window. */
@Component
public class FrequencyRule implements FraudRule {

    public static final String CODE = "FRECUENCIA_ALTA";

    private final FraudProperties properties;
    private final FraudCheckRepository checks;

    public FrequencyRule(FraudProperties properties, FraudCheckRepository checks) {
        this.properties = properties;
        this.checks = checks;
    }

    // shortcut: no per-account lock, two concurrent checks can let one extra through; add a lock if strict counting is required.
    @Override
    public Optional<Hit> evaluate(Context context) {
        Instant since = context.now().minus(properties.frequencyWindow());
        long recent = checks.countByOriginIbanAndCreatedAtGreaterThanEqual(context.originIban(), since);
        if (recent >= properties.frequencyMax()) {
            return Optional.of(new Hit(CODE, Verdict.BLOQUEADA));
        }
        return Optional.empty();
    }
}
