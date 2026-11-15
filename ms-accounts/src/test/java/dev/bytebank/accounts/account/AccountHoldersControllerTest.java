package dev.bytebank.accounts.account;

import dev.bytebank.accounts.IntegrationTest;
import dev.bytebank.accounts.client.AuthClient;
import dev.bytebank.accounts.client.AuthClient.UserInfo;
import dev.bytebank.common.iban.Iban;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountHoldersControllerTest extends IntegrationTest {

    private static final Long ANA_ID = 2L;
    private static final Long LUIS_ID = 3L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    AuthClient auth;

    private String iban;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from movements");
        jdbc.update("delete from accounts");

        Account account = new Account(Iban.generate(accounts.nextAccountNumber()));
        account.addHolder(ANA_ID);
        iban = accounts.save(account).getIban();
    }

    @Test
    void holderAddsClientByEmail() throws Exception {
        when(auth.findByEmail("luis@bytebank.test"))
                .thenReturn(new UserInfo(LUIS_ID, "luis@bytebank.test", "Luis", Role.CLIENTE));

        mockMvc.perform(post("/api/accounts/{iban}/holders", iban)
                        .with(authenticatedAs(ANA_ID, Role.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "luis@bytebank.test" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holderIds[1]").value(LUIS_ID));
    }

    @Test
    void nonHolderCannotAdd() throws Exception {
        mockMvc.perform(post("/api/accounts/{iban}/holders", iban)
                        .with(authenticatedAs(99L, Role.CLIENTE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "luis@bytebank.test" }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void holderSeesHolderNames() throws Exception {
        when(auth.findById(ANA_ID))
                .thenReturn(new UserInfo(ANA_ID, "ana@bytebank.test", "Ana", Role.CLIENTE));

        mockMvc.perform(get("/api/accounts/{iban}/holders", iban)
                        .with(authenticatedAs(ANA_ID, Role.CLIENTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ana@bytebank.test"))
                .andExpect(jsonPath("$[0].fullName").value("Ana"));
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
