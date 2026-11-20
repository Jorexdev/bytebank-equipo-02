package dev.bytebank.auth.bootstrap;

import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** SEG-03: el primer gestor de un entorno real se crea con variables de entorno y sin contraseña por defecto. */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class GestorBootstrapTest {

    private static final String EMAIL = "marta@bytebank.es";
    private static final String PASSWORD = "Una-Clave-Larga-2026";

    @Mock
    UserRepository users;

    // Coste 4 (el mínimo): el test no necesita un hash lento, solo uno real.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void doesNothingWhenItIsNotConfigured() {
        bootstrap("", "").run(null);

        verifyNoInteractions(users);
    }

    @Test
    void createsTheFirstGestor(CapturedOutput output) {
        bootstrap("  Marta@ByteBank.es ", PASSWORD).run(null);

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User gestor = saved.getValue();
        assertThat(gestor.getRole()).isEqualTo(Role.GESTOR);
        assertThat(gestor.getEmail()).isEqualTo(EMAIL); // igual que el registro: sin espacios y en minúsculas
        assertThat(gestor.getFullName()).isEqualTo("Marta Gestora");
        assertThat(passwordEncoder.matches(PASSWORD, gestor.getPasswordHash())).isTrue();
        assertThat(output).contains(EMAIL).doesNotContain(PASSWORD);
    }

    @Test
    void doesNotCreateASecondGestor(CapturedOutput output) {
        when(users.existsByRole(Role.GESTOR)).thenReturn(true);

        bootstrap(EMAIL, PASSWORD).run(null);

        verify(users, never()).save(any());
        assertThat(output).contains("BOOTSTRAP_GESTOR").doesNotContain(PASSWORD);
    }

    @Test
    void refusesToStartWithOnlyTheEmail() {
        assertThatThrownBy(() -> bootstrap(EMAIL, "").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_GESTOR_PASSWORD");
        verifyNoInteractions(users);
    }

    @Test
    void refusesToStartWithOnlyThePassword() {
        assertThatThrownBy(() -> bootstrap("", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_GESTOR_EMAIL")
                .message().doesNotContain(PASSWORD);
        verifyNoInteractions(users);
    }

    @Test
    void refusesToStartWithAShortPassword(CapturedOutput output) {
        String shortPassword = "Corta-2026"; // 10 caracteres

        assertThatThrownBy(() -> bootstrap(EMAIL, shortPassword).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12")
                .message().doesNotContain(shortPassword);
        assertThat(output).doesNotContain(shortPassword);
        verifyNoInteractions(users);
    }

    @Test
    void refusesToStartWithAPasswordLongerThanBcryptAccepts() {
        assertThatThrownBy(() -> bootstrap(EMAIL, "x".repeat(73)).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("72");
        verifyNoInteractions(users);
    }

    @Test
    void refusesToStartWhenTheEmailBelongsToAnotherUser() {
        when(users.existsByEmailIgnoreCase(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> bootstrap(EMAIL, PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(EMAIL);
        verify(users, never()).save(any());
    }

    private GestorBootstrap bootstrap(String email, String password) {
        return new GestorBootstrap(users, passwordEncoder, email, password, "Marta Gestora");
    }
}
