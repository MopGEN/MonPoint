package com.monpoint.notificaciones.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import com.monpoint.notificaciones.model.Notificacion;
import com.monpoint.notificaciones.model.EventoProcesado;
@Configuration
public class IndicesNotificaciones {
    // Se materializan ANTES de iniciar los listeners, también sobre volúmenes existentes.
    // Una incompatibilidad o duplicados preexistentes impiden arrancar: nunca se borran para ocultarlos.
    @Bean
    InitializingBean indices(MongoTemplate mongo) {
        return () -> {
            mongo.indexOps(Notificacion.class).createIndex(new Index().on("productoId", Direction.ASC)
                    .unique().partial(PartialIndexFilter.of(Criteria.where("activa").is(true)))
                    .named("producto_activo_unico"));
            mongo.indexOps(Notificacion.class).createIndex(new Index().on("productoId", Direction.ASC)
                    .on("activa", Direction.ASC).named("producto_activa"));
            mongo.indexOps(Notificacion.class).createIndex(new Index().on("leida", Direction.ASC).named("leida"));
            mongo.indexOps(EventoProcesado.class).createIndex(new Index().on("eventoId", Direction.ASC).unique().named("eventoId"));
        };
    }
}
