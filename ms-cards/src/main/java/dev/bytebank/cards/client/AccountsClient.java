package dev.bytebank.cards.client;

import dev.bytebank.common.http.InternalClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AccountsClient {

    private final RestClient client;

    public AccountsClient(InternalClients clients, @Value("${bytebank.services.accounts-url}") String accountsUrl) {
        this.client = clients.forService(accountsUrl);
    }

    public record AccountInfo(String iban, String status, BigDecimal balance, List<Long> holderIds) {

        public boolean isActive() {
            return "ACTIVA".equals(status);
        }

        public boolean isHeldBy(Long userId) {
            return holderIds != null && holderIds.contains(userId);
        }
    }

    public record MovementRequest(String reference, String iban, BigDecimal amount, String type, String concept) {
    }

    public record LedgerResult(String reference, BigDecimal balanceAfter) {
    }

    public AccountInfo getAccount(String iban) {
        return InternalClients.call(() -> client.get()
                .uri("/internal/accounts/{iban}", iban)
                .retrieve()
                .body(AccountInfo.class));
    }

    public LedgerResult debit(String reference, String iban, BigDecimal amount, String concept) {
        return post("/internal/accounts/debit", new MovementRequest(reference, iban, amount, "PAGO_TARJETA", concept));
    }

    public LedgerResult credit(String reference, String iban, BigDecimal amount, String concept) {
        return post("/internal/accounts/credit", new MovementRequest(reference, iban, amount, "INGRESO", concept));
    }

    private LedgerResult post(String path, MovementRequest request) {
        return InternalClients.call(() -> client.post()
                .uri(path)
                .body(request)
                .retrieve()
                .body(LedgerResult.class));
    }
}
