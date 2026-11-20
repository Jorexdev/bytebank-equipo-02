package dev.bytebank.auth.demo;

import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.common.security.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** SEG-03: un entorno con prod no puede tener usuarios con las contraseñas públicas de la demo. */
@ExtendWith(MockitoExtension.class)
class DemoUsersInProductionCheckTest {

    private static final String HASH = "$2a$10$hash-de-prueba-que-no-debe-salir";

    @Mock
    UserRepository users;

    @InjectMocks
    DemoUsersInProductionCheck check;

    @Test
    void allowsStartingWithoutDemoUsers() {
        assertThatCode(check::check).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhileDemoUsersExist() {
                when(users.findByEmailIgnoreCase("gestor@bytebank.test"))
                .thenReturn(Optional.of(user(1L, "gestor@bytebank.test", "Marta Gestora", Role.GESTOR)));
        when(users.findByEmailIgnoreCase("ana@bytebank.test"))
                .thenReturn(Optional.empty()); 
        when(users.findByEmailIgnoreCase("luis@bytebank.test"))
                .thenReturn(Optional.of(user(3L, "luis@bytebank.test", "Luis Cliente", Role.CLIENTE)));


        assertThatThrownBy(check::check)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gestor@bytebank.test (id 1)")
                .hasMessageContaining("luis@bytebank.test (id 3)")
                .hasMessageContaining("4.20") // dónde está el procedimiento de limpieza
                .message().doesNotContain(HASH, "Marta Gestora", "Luis Cliente");
    }

    private static User user(Long id, String email, String fullName, Role role) {
        User user = new User(email, HASH, fullName, role);
        ReflectionTestUtils.setField(user, "id", id); // el id lo asigna la base de datos
        return user;
    }
}
