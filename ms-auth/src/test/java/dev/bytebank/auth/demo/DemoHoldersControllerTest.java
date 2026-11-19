package dev.bytebank.auth.demo;

import dev.bytebank.auth.IntegrationTest;
import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.common.security.InternalKeyFilter;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SEG-03: ms-accounts solo siembra cuentas si auth_db contiene únicamente los usuarios de demo. */
@ActiveProfiles("demo")
@AutoConfigureMockMvc
class DemoHoldersControllerTest extends IntegrationTest {

    private static final String URL = "/internal/demo/holders";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DemoSeeder seeder;

    /** Cada test parte de una base con solo los tres usuarios de demo. */
    @BeforeEach
    void onlyDemoUsers() {
        jdbc.update("delete from users");
        seeder.run(null);
    }

    @Test
    void returnsTheIdsOfAnaAndLuis() throws Exception {
        Long ana = idOf("ana@bytebank.test");
        Long luis = idOf("luis@bytebank.test");

        mockMvc.perform(get(URL).header(InternalKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anaId").value(ana))
                .andExpect(jsonPath("$.luisId").value(luis));
    }

    @Test
    void refusesWhenThereIsAUserThatIsNotFromTheDemo() throws Exception {
        users.save(new User("maria.real@example.com", "hash", "María Real", Role.CLIENTE));

        mockMvc.perform(get(URL).header(InternalKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BASE_NO_DEMO"));
    }

        @Test
    void isNotReadyWhileTheDemoUsersDoNotExistYet() throws Exception {
        jdbc.update("delete from users"); // ms-auth recién arrancado: su DemoSeeder todavía no ha terminado

        mockMvc.perform(get(URL).header(InternalKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEMO_NO_PREPARADA"));
    }

    @Test
    void isNotReadyWhileADemoHolderIsMissing() throws Exception {
        jdbc.update("delete from users where email = 'luis@bytebank.test'");

        mockMvc.perform(get(URL).header(InternalKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEMO_NO_PREPARADA"));
    }

    @Test
    void requiresTheInternalKey() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    private Long idOf(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }
}
