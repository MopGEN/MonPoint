package com.monpoint.auth.exception;

/**
 * No existe un usuario con el id solicitado: se traduce a 404 Not Found.
 */
public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(String id) {
        super("No existe un usuario con id " + id);
    }
}
