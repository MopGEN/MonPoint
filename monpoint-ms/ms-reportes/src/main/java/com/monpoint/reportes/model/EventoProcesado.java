package com.monpoint.reportes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de idempotencia (Doc 04: {@code eventos_procesados}). El índice único sobre {@code eventoId} es la
 * última defensa: si dos entregas del mismo evento llegan a la vez, la segunda choca al insertar, su transacción
 * se revierte y al reintentar ya lo encuentra como procesado.
 */
@Document(collection = "eventos_procesados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProcesado {

    /** Se deja sin asignar para que MongoDB lo genere al insertar. */
    @Id
    private String id;

    @Indexed(unique = true)
    private String eventoId;

    private String tipo;

    private Instant fechaProcesado;

    public static EventoProcesado de(String eventoId, String tipo, Instant fechaProcesado) {
        EventoProcesado evento = new EventoProcesado();
        evento.eventoId = Objects.requireNonNull(eventoId, "El eventoId es obligatorio");
        evento.tipo = tipo;
        evento.fechaProcesado = fechaProcesado;
        return evento;
    }
}
