package com.monpoint.reportes.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/** Sobre de ProductoCreado, ProductoActualizado y ProductoDadoDeBaja (ms-inventario). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoEvento(String eventoId, String tipo, String version, String fecha, String origen,
                             Datos datos) {

    /** {@code fechaActualizacion} puede venir nula (documentos viejos de inventario sin auditoría). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String productoId, String nombre, BigDecimal precio, Boolean activo,
                        String fechaActualizacion) {
    }
}
