package dev.bytebank.accounts.account;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountControllerSecurityTest extends IntegrationTest {

    private static final Long CLIENT_ID = 1L;
    private static final Long OTHER_CLIENT_ID = 2L;
    private static final Long GESTOR_ID = 3L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    private String clientIban;
    private String otherClientIban;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from movements");
        jdbc.update("delete from accounts");

        clientIban = createAccount(CLIENT_ID);
        otherClientIban = createAccount(OTHER_CLIENT_ID);
    }

    @Test
    void clientCanOnlyListOwnAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts")
                .with(authenticatedAs(CLIENT_ID, Role.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].iban").value(clientIban))
                .andExpect(jsonPath("$[0].ownerId").value(CLIENT_ID));
    }

    @Test
    void gestorCanListAllAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts")
                .with(authenticatedAs(GESTOR_ID, Role.GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void clientCanAccessOwnAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/{iban}", clientIban)
                .with(authenticatedAs(CLIENT_ID, Role.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(clientIban));
    }

    @Test
    void clientCannotAccessAnotherUsersAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/{iban}", otherClientIban)
                .with(authenticatedAs(CLIENT_ID, Role.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorCanAccessAnotherUsersAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/{iban}", otherClientIban)
                .with(authenticatedAs(GESTOR_ID, Role.GESTOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(otherClientIban));
    }

    @Test
    void clientCannotAccessAnotherUsersStatement() throws Exception {
        mockMvc.perform(get("/api/accounts/{iban}/movements", otherClientIban)
                .with(authenticatedAs(CLIENT_ID, Role.CLIENTE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotChangeAccountStatus() throws Exception {
        mockMvc.perform(patch("/api/accounts/{iban}/status", otherClientIban)
                .with(authenticatedAs(CLIENT_ID, Role.CLIENTE))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "status": "BLOQUEADA"
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void gestorCanChangeAccountStatus() throws Exception {
        mockMvc.perform(patch("/api/accounts/{iban}/status", otherClientIban)
                .with(authenticatedAs(GESTOR_ID, Role.GESTOR))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "status": "BLOQUEADA"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOQUEADA"));
    }

    private String createAccount(Long ownerId) {
        String iban = Iban.generate(accounts.nextAccountNumber());
        return accounts.save(new Account(iban, ownerId)).getIban();
    }

    private RequestPostProcessor authenticatedAs(Long userId, Role role) {
        AuthUser user = new AuthUser(userId, userId + "@test.com", role);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));

        return authentication(auth);
    }
}
