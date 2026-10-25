package com.monpoint.ventas.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * ProductoCreado, ProductoActualizado y ProductoDadoDeBaja tal como los publica ms-inventario.
 * <p>
 * Los tres comparten forma, así que un solo record los cubre: el campo {@code tipo} del sobre los distingue.
 * Copiado de {@code RabbitInventarioEventPublisher.datosDe(Producto)}.
 * <p>
 * <b>Nótese que no llega {@code existencia}</b>, y es deliberado: la autoridad sobre el stock es exclusivamente
 * ms-inventario (Doc 04 §6.3). Si productos_replica guardara existencias, ms-ventas podría decidir sobre datos
 * desactualizados y vender lo que ya no hay.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoEvento(String eventoId, String tipo, String version, String fecha, String origen,
                             Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String productoId, String nombre, BigDecimal precio, Boolean activo,
                        String fechaActualizacion) {
    }
}
