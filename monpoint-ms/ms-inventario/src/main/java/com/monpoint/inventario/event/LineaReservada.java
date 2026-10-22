package com.monpoint.inventario.event;

/** Resultado de reservar una línea de venta: la existencia ya descontada y el umbral vigente del producto. */
public record LineaReservada(String productoId, String nombre, int cantidad, int existenciaResultante,
                             int umbralStock) {
}
