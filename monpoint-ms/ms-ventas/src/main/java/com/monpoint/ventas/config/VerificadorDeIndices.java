package com.monpoint.ventas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Comprueba al arrancar que los índices que ms-ventas necesita existen de verdad, y <b>detiene el arranque</b>
 * si falta alguno.
 * <p>
 * Por qué existe esta clase. El resto de los servicios usa {@code spring.data.mongodb.auto-index-creation=true},
 * que crea los índices de forma perezosa desde las anotaciones. ms-ventas no: sus índices los crea el guion
 * {@code infra/mongo-init/02-indices-ventas.js} al aprovisionar la base, que es el mecanismo que pide el
 * Doc 04 §2 y que mantiene el esquema cerrado —un {@code @Indexed} nuevo no se materializa solo, pasa por el
 * Product Owner—.
 * <p>
 * El riesgo de ese mecanismo es que los guiones de {@code mongo-init} solo se ejecutan sobre un volumen vacío.
 * Quien levante ms-ventas contra un volumen preexistente se quedaría <b>sin el índice único sobre
 * {@code eventoId}</b>, y entonces la idempotencia dejaría de estar garantizada: solo quedaría la comprobación
 * previa, que no protege contra dos entregas simultáneas del mismo evento. Ese fallo sería silencioso y solo
 * aparecería bajo carga.
 * <p>
 * Fallar al arrancar con un mensaje explícito es preferible a descontar stock dos veces en la demostración.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificadorDeIndices implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        exigirIndiceUnico("eventos_procesados", "eventoId");
        exigirIndiceUnico("ventas", "folio");
        exigirIndice("ventas", "estado");
        log.info("evento=INDICES_VERIFICADOS resultado=ok");
    }

    private void exigirIndiceUnico(String coleccion, String campo) {
        if (indices(coleccion).stream().noneMatch(i -> i.isUnique() && cubre(i, campo))) {
            throw new IllegalStateException(mensaje(coleccion, campo, true));
        }
    }

    private void exigirIndice(String coleccion, String campo) {
        if (indices(coleccion).stream().noneMatch(i -> cubre(i, campo))) {
            throw new IllegalStateException(mensaje(coleccion, campo, false));
        }
    }

    private List<IndexInfo> indices(String coleccion) {
        return mongoTemplate.indexOps(coleccion).getIndexInfo();
    }

    private static boolean cubre(IndexInfo indice, String campo) {
        return indice.getIndexFields().stream().anyMatch(c -> campo.equals(c.getKey()));
    }

    private static String mensaje(String coleccion, String campo, boolean unico) {
        return ("Falta el índice%s sobre '%s.%s'. Los índices de ms-ventas los crea "
                + "infra/mongo-init/02-indices-ventas.js, y mongo-init solo se ejecuta sobre un volumen vacío. "
                + "Ejecutar: docker compose down -v && docker compose up -d")
                .formatted(unico ? " ÚNICO" : "", coleccion, campo);
    }
}
