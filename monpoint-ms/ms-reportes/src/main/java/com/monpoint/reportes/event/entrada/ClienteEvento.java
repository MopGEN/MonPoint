package com.monpoint.reportes.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Sobre de ClienteCreado y ClienteActualizado (ms-clientes). No existe evento de baja. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteEvento(String eventoId, String tipo, String version, String fecha, String origen,
                            Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String clienteId, String nombre, String correo, String telefono, String direccion,
                        String fechaActualizacion) {
    }
}
