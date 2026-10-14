package com.monpoint.inventario.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * VentaCreada tal como la consume ms-inventario (H-023): sobre común + {@code datos}.
 * <p>
 * ⚠️ PROVISIONAL: de {@code datos} solo se necesita lo que H-023 describe (las líneas con producto y cantidad);
 * los nombres {@code ventaId}, {@code lineas}, {@code productoId} y {@code cantidad} son una propuesta hasta que
 * ms-ventas (Alejandro) y el catálogo H-008 los fijen. Se ignoran los campos desconocidos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VentaCreadaEvento(String eventoId, String tipo, String version, String fecha, String origen,
                                Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String ventaId, List<Linea> lineas) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Linea(String productoId, int cantidad) {
    }
}
