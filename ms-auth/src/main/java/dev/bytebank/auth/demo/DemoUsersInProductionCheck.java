package dev.bytebank.auth.demo;

import dev.bytebank.auth.user.UserRepository;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Con el perfil prod, ms-auth no arranca si existe algún usuario de demostración (SEG-03).
 * Se comprueba al crear el bean, antes de que Tomcat acepte peticiones: nadie llega a iniciar sesión
 * con las contraseñas públicas de la demo.
 */
@Component
@Profile("prod")
public class DemoUsersInProductionCheck {

    private final UserRepository users;

    public DemoUsersInProductionCheck(UserRepository users) {
        this.users = users;
    }

    @PostConstruct
    void check() {
        List<String> found = DemoSeeder.EMAILS.stream()
                .map(users::findByEmailIgnoreCase)
                .flatMap(Optional::stream)
                .map(user -> user.getEmail() + " (id " + user.getId() + ")")
                .toList();
        if (!found.isEmpty()) {
            throw new IllegalStateException("Con el perfil prod no puede haber usuarios de demostración: tienen "
                    + "contraseñas públicas. Encontrados: " + String.join(", ", found) + ". Bórralos junto con "
                    + "sus datos en el resto de servicios siguiendo el procedimiento de docs/diagnostico.md "
                    + "(sección 4.20) y vuelve a arrancar.");
        }
    }
}
