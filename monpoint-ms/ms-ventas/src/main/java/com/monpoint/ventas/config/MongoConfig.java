package com.monpoint.ventas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Auditoría de fechas y gestor de transacciones de MongoDB.
 * <p>
 * El gestor de transacciones NO es decorativo en ms-ventas: el registro de una venta (tarea 4.1) incrementa el
 * folio en {@code contadores} y escribe el documento de {@code ventas}; la resolución de la saga (tarea 4.4)
 * registra el {@code eventoId} y cambia el estado de la venta. En ambos casos son dos documentos que tienen
 * que confirmarse juntos o no confirmarse.
 * <p>
 * Esto exige que MongoDB corra como replica set. Una instancia suelta rechaza las transacciones con
 * <i>«Transaction numbers are only allowed on a replica set member or mongos»</i>; por eso la cadena de
 * conexión lleva {@code replicaSet=rs0}.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }
}
