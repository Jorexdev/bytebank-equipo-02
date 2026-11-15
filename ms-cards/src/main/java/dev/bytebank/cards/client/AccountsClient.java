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

    public AccountInfo getAccount(String iban) {
        return InternalClients.call(() -> client.get()
                .uri("/internal/accounts/{iban}", iban)
                .retrieve()
                .body(AccountInfo.class));
    }
}
