package dev.bytebank.auth.demo;

import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.common.security.Role;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuarios de demostración (perfil demo), con contraseñas publicadas en el README.
 * ms-accounts no usa ids fijos: pregunta los de Ana y Luis a /internal/demo/holders.
 */
@Component
@Profile("demo")
@Order(1) // antes que GestorBootstrap (2)

public class DemoSeeder implements ApplicationRunner {

    static final String GESTOR_EMAIL = "gestor@bytebank.test";
    static final String ANA_EMAIL = "ana@bytebank.test";
    static final String LUIS_EMAIL = "luis@bytebank.test";
    static final List<String> EMAILS = List.of(GESTOR_EMAIL, ANA_EMAIL, LUIS_EMAIL);

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DemoSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new User(GESTOR_EMAIL, passwordEncoder.encode("Gestor1234!"), "Marta Gestora", Role.GESTOR));
        users.save(new User(ANA_EMAIL, passwordEncoder.encode("Cliente1234!"), "Ana Cliente", Role.CLIENTE));
        users.save(new User(LUIS_EMAIL, passwordEncoder.encode("Cliente1234!"), "Luis Cliente", Role.CLIENTE));
        log.info("Usuarios de demostración creados");
    }
}
