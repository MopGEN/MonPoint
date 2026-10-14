package com.monpoint.inventario.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Registro de un evento ya procesado (Doc 04 §10, RNF-16). El índice ÚNICO sobre {@code eventoId} es la clave
 * de idempotencia: si dos entregas del mismo evento compiten, la segunda inserción falla.
 */
@Document(collection = "eventos_procesados")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventoProcesado {

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventoId;

    private String tipo;

    private Instant fechaProcesado;

    public static EventoProcesado de(String eventoId, String tipo) {
        EventoProcesado evento = new EventoProcesado();
        evento.eventoId = Objects.requireNonNull(eventoId, "El eventoId es obligatorio");
        evento.tipo = tipo;
        evento.fechaProcesado = Instant.now();
        return evento;
    }
}
