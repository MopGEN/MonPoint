package com.monpoint.ventas.service;

import com.monpoint.ventas.model.Contador;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FolioService {
    private final MongoTemplate mongo;

    /** Participa en la transacción de la venta; no hace lectura-suma-escritura. */
    public long siguiente() {
        try {
            Contador contador = mongo.findAndModify(
                    Query.query(Criteria.where("_id").is(Contador.VENTA_FOLIO)),
                    new Update().inc("valor", 1L),
                    FindAndModifyOptions.options().upsert(true).returnNew(true), Contador.class);
            return Objects.requireNonNull(contador).getValor();
        } catch (DuplicateKeyException ex) {
            // Dos primeras ventas pueden intentar crear la misma clave fija simultáneamente.
            throw new CreacionConcurrenteDeContador(ex);
        }
    }

    static class CreacionConcurrenteDeContador extends RuntimeException {
        CreacionConcurrenteDeContador(Throwable causa) { super(causa); }
    }
}
