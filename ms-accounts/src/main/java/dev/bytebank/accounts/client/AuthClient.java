package dev.bytebank.accounts.client;

import dev.bytebank.common.http.InternalClients;
import dev.bytebank.common.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Consultas a ms-auth por sus rutas internas (usuarios). */
@Component
public class AuthClient {

    private final RestClient client;

    public AuthClient(InternalClients clients, @Value("${bytebank.services.auth-url}") String authUrl) {
        this.client = clients.forService(authUrl);
    }

    public record UserInfo(Long id, String email, String fullName, Role role) {
    }

        /** Titulares de las cuentas de demostración: los ids reales de Ana y Luis en ms-auth. */
    public record DemoHolders(Long anaId, Long luisId) {
    }

    /**
     * 200 si ms-auth solo tiene usuarios de demo; ApiException 409/404 si no se debe sembrar;
     * RemoteUnavailableException si ms-auth no responde o su demo aún no está lista (503).
     */
    public DemoHolders demoHolders() {
        return InternalClients.call(() -> client.get()
                .uri("/internal/demo/holders")
                .retrieve()
                .body(DemoHolders.class));
    }


    public UserInfo findByEmail(String email) {
        return InternalClients.call(() -> client.get()
                .uri("/internal/users/by-email?email={email}", email)
                .retrieve()
                .body(UserInfo.class));
    }

    public UserInfo findById(Long id) {
        return InternalClients.call(() -> client.get()
                .uri("/internal/users/{id}", id)
                .retrieve()
                .body(UserInfo.class));
    }
}
