package com.monpoint.ventas.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Respuesta afirmativa de ms-inventario a VentaCreada. Copiado de
 * {@code RabbitInventarioEventPublisher.publicarStockReservado}.
 * <p>
 * Consumirlo lleva la venta a CONFIRMADA (tarea 4.4). {@code existenciaResultante} llega por línea pero
 * ms-ventas NO lo almacena: sigue sin ser autoridad sobre el stock.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockReservadoEvento(String eventoId, String tipo, String version, String fecha, String origen,
                                   Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String ventaId, List<Linea> lineas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Linea(String productoId, int cantidad, int existenciaResultante) {
    }
}
