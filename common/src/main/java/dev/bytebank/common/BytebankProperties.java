package dev.bytebank.common;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Configuración común de los servicios. Los secretos llegan siempre por variables de entorno
 * (JWT_SECRET, INTERNAL_KEY) y no tienen valor por defecto: sin ellos el servicio no arranca.
 */
@Validated
@ConfigurationProperties(prefix = "bytebank")
public record BytebankProperties(@Valid @NotNull Jwt jwt, @NotBlank String internalKey, Security security) {

    public BytebankProperties {
        if (security == null) {
            security = new Security(List.of());
        }
    }

    public record Jwt(
            @NotBlank @Size(min = 32, message = "el secreto JWT debe tener al menos 32 caracteres") String secret,
            Long expirationMinutes) {

        public long expirationMinutesOrDefault() {
            return expirationMinutes == null ? 60 : expirationMinutes;
        }
    }

    /** Rutas públicas propias de cada servicio (por ejemplo, el login en ms-auth). */
    public record Security(List<String> publicPaths) {

        public Security {
            publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
        }
    }
}
