package com.monpoint.auth.exception;

/**
 * Nadie puede cambiar su propio rol (H-013), ni un VENDEDOR ni un ADMIN: se traduce a 400.
 */
public class ModificacionRolPropioException extends RuntimeException {

    public ModificacionRolPropioException() {
        super("No puedes cambiar tu propio rol");
    }
}
