package dev.bytebank.fraud;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Thresholds for the antifraud rules.
 *
 * @param blockThreshold          maximum amount per transaction
 * @param externalReviewThreshold amount from which a transfer to another bank goes to review
 * @param frequencyMax            validations allowed per account inside the window
 * @param frequencyWindow         sliding window of the frequency rule
 */
@Validated
@ConfigurationProperties(prefix = "bytebank.fraud")
public record FraudProperties(@NotNull @Positive BigDecimal blockThreshold,
                              @NotNull @Positive BigDecimal externalReviewThreshold,
                              @Positive int frequencyMax,
                              @NotNull Duration frequencyWindow) {
}
