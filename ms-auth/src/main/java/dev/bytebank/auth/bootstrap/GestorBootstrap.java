package dev.bytebank.auth.bootstrap;

import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Crea el primer gestor de un entorno real a partir de BOOTSTRAP_GESTOR_EMAIL y
 * BOOTSTRAP_GESTOR_PASSWORD (SEG-03).
 *
 * Sin las variables no hace nada. Si la configuración está incompleta o no es
 * válida, el servicio no arranca.
 * Solo crea el gestor si todavía no existe ninguno: después del primer
 * arranque, las variables se pueden
 * (y deben) quitar. La contraseña no aparece nunca en el log ni en los mensajes
 * de error.
 */
@Component
@Order(2) // después del DemoSeeder (1): con demo, ya existe el gestor de demo y aquí no
          // se crea otro
public class GestorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GestorBootstrap.class);

    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MAX_PASSWORD_BYTES = 72; // BCrypt ignora lo que pase de 72 bytes

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String fullName;

    public GestorBootstrap(UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${bytebank.bootstrap-gestor.email:}") String email,
            @Value("${bytebank.bootstrap-gestor.password:}") String password,
            @Value("${bytebank.bootstrap-gestor.full-name:Gestor principal}") String fullName) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasEmail = !email.isBlank();
        boolean hasPassword = !password.isEmpty();
        if (!hasEmail && !hasPassword) {
            return;
        }
        validate(hasEmail, hasPassword);

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByRole(Role.GESTOR)) {
            log.warn("Ya existe un gestor: se ignoran BOOTSTRAP_GESTOR_EMAIL y BOOTSTRAP_GESTOR_PASSWORD. "
                    + "Quítalas de la configuración del entorno.");
            return;
        }
        if (users.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("BOOTSTRAP_GESTOR_EMAIL: " + normalizedEmail
                    + " ya pertenece a otro usuario. Usa un email que no esté registrado.");
        }
        users.save(new User(normalizedEmail, passwordEncoder.encode(password), fullName.trim(), Role.GESTOR));
        log.info("Primer gestor creado: {}. Quita BOOTSTRAP_GESTOR_PASSWORD de la configuración del entorno.",
                normalizedEmail);
    }

    /** Errores de configuración: se comprueban antes de tocar la base de datos. */
    private void validate(boolean hasEmail, boolean hasPassword) {
        if (!hasEmail) {
            throw new IllegalStateException("Falta BOOTSTRAP_GESTOR_EMAIL: se ha indicado la contraseña sin el email.");
        }
        if (!hasPassword) {
            throw new IllegalStateException(
                    "Falta BOOTSTRAP_GESTOR_PASSWORD: se ha indicado el email sin la contraseña.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("BOOTSTRAP_GESTOR_PASSWORD debe tener al menos "
                    + MIN_PASSWORD_LENGTH + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalStateException("BOOTSTRAP_GESTOR_PASSWORD no puede pasar de "
                    + MAX_PASSWORD_BYTES + " bytes (límite de BCrypt).");
        }
    }
}