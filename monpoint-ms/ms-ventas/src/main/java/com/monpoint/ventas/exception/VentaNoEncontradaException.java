package com.monpoint.ventas.exception;

/**
 * No existe una venta con el identificador consultado (actividad 5.2).
 * <p>
 * Es distinta de {@link VentaInvalidaException}: aquella significa que la petición está mal formada —un
 * identificador que no es un ObjectId, por ejemplo— y produce 400; esta significa que la petición era
 * correcta pero el recurso no existe, y produce 404. Confundirlas le quitaría al cliente la única señal
 * que le permite distinguir «escribiste mal el folio» de «esa venta no está».
 */
public class VentaNoEncontradaException extends RuntimeException {
    public VentaNoEncontradaException(String ventaId) {
        super("No existe una venta con el identificador " + ventaId);
    }
}
