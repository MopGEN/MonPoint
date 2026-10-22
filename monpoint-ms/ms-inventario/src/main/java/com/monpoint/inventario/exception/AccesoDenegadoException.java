package com.monpoint.inventario.exception;

/** El rol del usuario no alcanza para la operación ➔ 403 (RNF-07). */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
