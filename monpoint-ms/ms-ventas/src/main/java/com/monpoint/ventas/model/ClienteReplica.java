package com.monpoint.ventas.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Réplica del nombre del cliente para las ventas.
 * ms-clientes no publica bajas: esta réplica no puede reflejar clientes eliminados.
 */
@Document(collection = "clientes_replica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClienteReplica {

    /** Conserva el identificador del cliente en ms-clientes. */
    @Id
    private String id;

    private String nombre;

    /** Fecha del evento de origen; no se actualiza mediante auditoría de persistencia. */
    private Instant fechaActualizacion;

    public static ClienteReplica crear(String id, String nombre, Instant fechaActualizacion) {
        ClienteReplica cliente = new ClienteReplica();
        cliente.id = id;
        cliente.actualizarDesde(nombre, fechaActualizacion);
        return cliente;
    }

    /** El consumidor decide previamente si corresponde aplicar el evento. */
    public void actualizarDesde(String nombre, Instant fechaActualizacion) {
        this.nombre = nombre;
        this.fechaActualizacion = fechaActualizacion;
    }
}
