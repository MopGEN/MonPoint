package com.monpoint.clientes.exception;

/** No existe el recurso pedido ➔ 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
