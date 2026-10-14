package com.monpoint.auth.exception;

/**
 * El correo ya pertenece a otro usuario (H-011, H-072): se traduce a 409 Conflict.
 */
public class CorreoDuplicadoException extends RuntimeException {

    public CorreoDuplicadoException(String correo) {
        super("Ya existe un usuario con el correo " + correo);
    }
}
