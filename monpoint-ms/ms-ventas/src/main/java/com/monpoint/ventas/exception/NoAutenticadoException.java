package com.monpoint.ventas.exception;

public class NoAutenticadoException extends RuntimeException {
    public NoAutenticadoException() { super("Se requiere un token válido. Inicia sesión para obtener uno."); }
}
