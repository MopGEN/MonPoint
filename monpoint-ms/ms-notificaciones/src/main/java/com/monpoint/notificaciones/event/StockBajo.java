package com.monpoint.notificaciones.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockBajo(String eventoId, String tipo, String version, String fecha, String origen, Datos datos) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String productoId, String nombre, Integer existenciaResultante) {}
}
