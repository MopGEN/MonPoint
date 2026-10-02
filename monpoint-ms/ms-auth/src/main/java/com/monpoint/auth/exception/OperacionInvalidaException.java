package com.monpoint.auth.exception;

/**
 * Operación que viola una regla de integridad del dominio (p. ej. un administrador
 * desactivando su propia cuenta): se traduce a 400 Bad Request.
 */
public class OperacionInvalidaException extends RuntimeException {

    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
