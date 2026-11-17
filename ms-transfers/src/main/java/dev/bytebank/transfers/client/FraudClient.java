package dev.bytebank.transfers.client;

import dev.bytebank.common.http.InternalClients;
import dev.bytebank.common.http.RemoteUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sends each transfer to ms-fraud before it is executed.
 * Network or 5xx failures surface as {@link dev.bytebank.common.http.RemoteUnavailableException}, so the
 * transfer is not created without a verdict.
 */
@Component
public class FraudClient {

    public enum Verdict {
        APROBADA,
        REVISAR,
        BLOQUEADA
    }

    public record CheckRequest(String originIban, String destinationIban, Long userId, BigDecimal amount) {
    }

    public record CheckResponse(Long checkId, Verdict verdict, List<String> reasons) {

        public CheckResponse {
            if (verdict == null) {
                throw new IllegalArgumentException("ms-fraud returned no verdict");
            }
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    private final RestClient client;

    public FraudClient(InternalClients clients, @Value("${bytebank.services.fraud-url}") String fraudUrl) {
        this.client = clients.forService(fraudUrl);
    }

    public CheckResponse check(String originIban, String destinationIban, Long userId, BigDecimal amount) {
        return InternalClients.call(() -> {
            CheckResponse response = client.post()
                    .uri("/internal/fraud/check")
                    .body(new CheckRequest(originIban, destinationIban, userId, amount))
                    .retrieve()
                    .body(CheckResponse.class);
            if (response == null) {
                throw new RemoteUnavailableException("ms-fraud returned no body");
            }
            return response;
        });
    }
}
