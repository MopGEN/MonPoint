package com.monpoint.ventas.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** Registro de un evento procesado para la idempotencia del consumidor. */
@Document(collection = "eventos_procesados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProcesado {

    /** Se deja sin asignar para que MongoDB lo genere al insertar. */
    @Id
    private String id;

    /**
     * Documenta la intención: auto-index-creation está desactivado.
     * El guion de aprovisionamiento crea el índice y VerificadorDeIndices comprueba su existencia.
     */
    @Indexed(unique = true)
    private String eventoId;

    private String tipo;

    private Instant fechaProcesado;

    public static EventoProcesado de(String eventoId, String tipo, Instant fechaProcesado) {
        EventoProcesado evento = new EventoProcesado();
        evento.eventoId = eventoId;
        evento.tipo = tipo;
        evento.fechaProcesado = fechaProcesado;
        return evento;
    }
}
