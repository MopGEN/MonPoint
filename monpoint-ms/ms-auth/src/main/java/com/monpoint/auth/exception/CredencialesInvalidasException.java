package com.monpoint.auth.exception;

/**
 * Login rechazado (H-010). El mensaje es deliberadamente opaco: no revela si falló el correo,
 * la contraseña o el estado de la cuenta, para no permitir adivinar qué correos existen.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Credenciales incorrectas");
    }
}
