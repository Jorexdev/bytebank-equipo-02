package dev.bytebank.auth.demo;

import dev.bytebank.auth.IntegrationTest;
import dev.bytebank.common.security.InternalKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sin el perfil demo, el endpoint no existe: un entorno real nunca puede dar ids para sembrar cuentas. */
@ActiveProfiles("default")
@AutoConfigureMockMvc
class DemoHoldersWithoutDemoProfileTest extends IntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void doesNotExistWithoutTheDemoProfile() throws Exception {
        mockMvc.perform(get("/internal/demo/holders").header(InternalKeyFilter.HEADER, INTERNAL_KEY))
                .andExpect(status().isNotFound());
    }
}
