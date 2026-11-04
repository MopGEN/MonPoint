package com.monpoint.reportes.exception;

public class VentaNoDisponibleException extends RuntimeException {

    public static final String CODIGO = "VENTA_NO_DISPONIBLE";

    public VentaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}