package com.monpoint.reportes.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sobre de VentaConfirmada y VentaCancelada (ms-ventas). Ambos comparten {@code datos}; {@code motivo} solo
 * llega en la cancelada. Los campos que aquí no se usan ({@code clienteId}, {@code fechaCreacion}) se declaran
 * igual para documentar el contrato; los desconocidos se ignoran para que un campo nuevo no rompa el consumo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VentaCierreEvento(String eventoId, String tipo, String version, String fecha, String origen,
                                Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String ventaId, Long folio, String clienteId, String clienteNombre, BigDecimal total,
                        String fechaCreacion, String fechaResolucion, List<Linea> lineas, String motivo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Linea(String productoId, String nombreProducto, Integer cantidad, BigDecimal precioUnitario) {
    }
}
