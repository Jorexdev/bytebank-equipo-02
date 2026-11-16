package dev.bytebank.fraud.rules;

import dev.bytebank.fraud.FraudProperties;
import dev.bytebank.fraud.check.Verdict;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Límite por operación: los importes a partir del umbral se bloquean. */
@Component
public class AmountRule implements FraudRule {

    public static final String CODE = "IMPORTE_MAXIMO";

    private final FraudProperties properties;

    public AmountRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Hit> evaluate(Context context) {
        if (context.amount().compareTo(properties.blockThreshold()) >= 0) {
            return Optional.of(new Hit(CODE, Verdict.BLOQUEADA));
        }
        return Optional.empty();
    }
}
