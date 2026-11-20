package dev.bytebank.cards.card;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bytebank.cards.payment.CardPayment;
import dev.bytebank.cards.payment.PaymentStatus;
import dev.bytebank.common.error.GlobalExceptionHandler;
import dev.bytebank.common.http.RemoteUnavailableException;
import dev.bytebank.common.security.AuthUser;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CardControllerTest {

    private final CardService service = mock(CardService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CardController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        var user = new AuthUser(2L, "ana@bytebank.test", Role.CLIENTE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsCreatedPayment() throws Exception {
        CardPayment payment = new CardPayment("CARD-abc", 1L, new BigDecimal("42.50"), "Supermercado",
                Instant.parse("2026-11-16T10:00:00Z"));
        payment.approve();
        when(service.pay(eq(1L), eq(new BigDecimal("42.50")), eq("Supermercado"), any())).thenReturn(payment);

        mvc.perform(post("/api/cards/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":42.50,\"merchant\":\"Supermercado\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APROBADO"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist());
    }

    @Test
    void rejectsInvalidPaymentData() throws Exception {
        mvc.perform(post("/api/cards/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":42.501,\"merchant\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATOS_NO_VALIDOS"));
    }

    @Test
    void returnsServiceUnavailableWhenAccountsIsDown() throws Exception {
        when(service.pay(eq(1L), eq(new BigDecimal("42.50")), eq("Supermercado"), any()))
                .thenThrow(new RemoteUnavailableException("ms-accounts no responde"));

        mvc.perform(post("/api/cards/1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":42.50,\"merchant\":\"Supermercado\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICIO_NO_DISPONIBLE"));
    }
}
