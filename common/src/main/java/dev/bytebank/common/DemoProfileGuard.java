package dev.bytebank.common;

import org.springframework.context.annotation.Profile;

/**
 * Impide arrancar un servicio con los perfiles demo y prod a la vez (SEG-03).
 * Solo se crea en esa combinación, y al crearse lanza el error.
 */
@Profile("demo & prod")
public class DemoProfileGuard {

    public DemoProfileGuard() {
        throw new IllegalStateException("Los perfiles demo y prod no pueden estar activos a la vez: demo crea "
                + "usuarios con contraseñas públicas. Quita demo de SPRING_PROFILES_ACTIVE.");
    }

}
