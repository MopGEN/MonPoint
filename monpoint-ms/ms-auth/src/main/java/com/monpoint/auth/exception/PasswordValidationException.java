package com.monpoint.auth.exception;

import java.util.List;

/**
 * La contraseña incumple una o más reglas de la política (H-012).
 * <p>
 * Conserva el desglose completo para que la respuesta 400 indique todas las reglas
 * que faltan, no solo la primera.
 */
public class PasswordValidationException extends RuntimeException {

    private final List<String> errores;

    public PasswordValidationException(List<String> errores) {
        super("La contraseña no cumple la política de seguridad");
        this.errores = List.copyOf(errores);
    }

    public List<String> getErrores() {
        return errores;
    }
}
