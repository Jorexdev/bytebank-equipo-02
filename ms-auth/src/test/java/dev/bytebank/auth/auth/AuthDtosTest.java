package dev.bytebank.auth.auth;

import dev.bytebank.auth.auth.AuthDtos.LoginRequest;
import dev.bytebank.auth.auth.AuthDtos.RegisterRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Los DTOs con contraseña no deben mostrarla si acaban en un log (SEG-02). */
class AuthDtosTest {

    private static final String PASSWORD = "Secreta-123456";

    @Test
    void loginRequestToStringHidesThePassword() {
        String text = new LoginRequest("ana@bytebank.test", PASSWORD).toString();

        assertThat(text).doesNotContain(PASSWORD).contains("ana@bytebank.test");
    }

    @Test
    void registerRequestToStringHidesThePassword() {
        String text = new RegisterRequest("ana@bytebank.test", PASSWORD, "Ana Cliente").toString();

        assertThat(text).doesNotContain(PASSWORD).contains("ana@bytebank.test", "Ana Cliente");
    }

    @Test
    void thePasswordIsStillAvailableToTheService() {
        assertThat(new LoginRequest("ana@bytebank.test", PASSWORD).password()).isEqualTo(PASSWORD);
    }
}
