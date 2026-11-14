package dev.bytebank.auth.auth;

import dev.bytebank.auth.auth.AuthDtos.LoginRequest;
import dev.bytebank.auth.auth.AuthDtos.RegisterRequest;
import dev.bytebank.auth.auth.AuthDtos.TokenResponse;
import dev.bytebank.auth.user.User;
import dev.bytebank.auth.user.UserRepository;
import dev.bytebank.auth.user.UserResponse;
import dev.bytebank.common.error.ApiException;
import dev.bytebank.common.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    /** El registro público siempre crea clientes; los gestores solo los crea el seeder o un administrador. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_YA_REGISTRADO", "Ya existe un usuario con ese email");
        }
        User user = users.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.CLIENTE));
        log.info("Usuario registrado id={}", user.getId());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        log.info("Intento de login para {}", request.email());
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "CREDENCIALES_INCORRECTAS",
                        "Email o contraseña incorrectos"));
        log.info("Login correcto id={}", user.getId());
        return new TokenResponse(jwtIssuer.issue(user), "Bearer", jwtIssuer.expirationSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return users.findById(id).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("USUARIO_NO_ENCONTRADO", "No existe el usuario " + id));
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        return users.findByEmailIgnoreCase(email.trim()).map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("USUARIO_NO_ENCONTRADO", "No existe un usuario con ese email"));
    }
}
