package dev.bytebank.auth.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "la contraseña debe tener entre 8 y 72 caracteres") String password,
            @NotBlank @Size(max = 120) String fullName) {

        /** No muestra la contraseña: el toString() por defecto de un record incluye todos los campos. */
        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=****, fullName=" + fullName + "]";
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        /** No muestra la contraseña: el toString() por defecto de un record incluye todos los campos. */
        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=****]";
        }
    }

    public record TokenResponse(String token, String tokenType, long expiresInSeconds) {
    }
}
