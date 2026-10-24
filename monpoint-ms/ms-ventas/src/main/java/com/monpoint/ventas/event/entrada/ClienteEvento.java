package com.monpoint.ventas.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ClienteCreado y ClienteActualizado tal como los publica ms-clientes.
 * Copiado de {@code RabbitClienteEventPublisher.datosDe(Cliente)}.
 * <p>
 * ms-ventas solo necesita {@code clienteId}, {@code nombre} y {@code fechaActualizacion} para su réplica
 * (Doc 04 §6.4); {@code correo}, {@code telefono} y {@code direccion} se declaran porque llegan, pero no se
 * almacenan. Declararlos evita que un cambio en ms-clientes pase inadvertido.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClienteEvento(String eventoId, String tipo, String version, String fecha, String origen,
                            Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String clienteId, String nombre, String correo, String telefono, String direccion,
                        String fechaActualizacion) {
    }
}
