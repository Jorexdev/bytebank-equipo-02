package dev.bytebank.auth.demo;

import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.common.error.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Da a ms-accounts los titulares de las cuentas de demostración (SEG-03).
 *
 * Solo existe con el perfil demo. Si auth_db tiene algún usuario que no es de demo, responde 409
 * BASE_NO_DEMO: sembrar cuentas regalaría saldo a clientes de verdad, y ms-accounts no lo vuelve a intentar.
 * Si faltan Ana o Luis (el DemoSeeder de ms-auth aún no ha terminado), responde 503 DEMO_NO_PREPARADA
 * y ms-accounts lo reintenta más tarde.
 */

@RestController
@Profile("demo")
@RequestMapping("/internal/demo")
public class DemoHoldersController {

    private final UserRepository users;

    public DemoHoldersController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/holders")
    public DemoHolders holders() {
        long demoUsers = DemoSeeder.EMAILS.stream().filter(users::existsByEmailIgnoreCase).count();
        if (users.count() != demoUsers) {
            throw notDemo("Hay usuarios que no son de demostración");
        }
        return new DemoHolders(idOf(DemoSeeder.ANA_EMAIL), idOf(DemoSeeder.LUIS_EMAIL));
    }

    private Long idOf(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(User::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEMO_NO_PREPARADA",
                        "Todavía no existe el usuario de demostración " + email));
    }

    private static ApiException notDemo(String message) {
        return ApiException.conflict("BASE_NO_DEMO", message);
    }
}
