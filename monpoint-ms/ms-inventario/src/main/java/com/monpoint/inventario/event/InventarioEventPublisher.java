package com.monpoint.inventario.event;

import com.monpoint.inventario.model.Producto;

import java.util.List;

/**
 * Puerto de salida de los eventos de ms-inventario (RF-2.3, RF-2.4, RF-2.5, RF-2.6).
 * <p>
 * Contrato fail-safe: una implementación nunca propaga excepciones al llamador. Si el broker falla, lo
 * registra y la operación de inventario sigue su curso.
 */
public interface InventarioEventPublisher {

    void publicarProductoCreado(Producto producto);

    void publicarProductoActualizado(Producto producto);

    void publicarProductoDadoDeBaja(Producto producto);

    /** Reserva aprobada: la existencia resultante de cada producto (H-023). */
    void publicarStockReservado(String ventaId, List<LineaReservada> lineas);

    /** Reserva rechazada: no se descontó nada; indica el producto y el motivo (H-023, RF-2.5). */
    void publicarStockRechazado(String ventaId, String productoId, String motivo);

    /** Una existencia quedó igual o bajo su umbral (H-024). */
    void publicarStockBajo(LineaReservada linea);
}
