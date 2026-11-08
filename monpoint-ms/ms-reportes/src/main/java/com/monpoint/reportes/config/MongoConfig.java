package com.monpoint.reportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
//MongoTransactionManager: hace que @Transactional funcione con Mongo. Spring Boot no lo crea solo, y las transacciones requieren el replica set rs0.
//MongoDatabaseFactory: da acceso a la base configurada; Spring lo pasa como parámetro.
//Para qué lo usaremos: en los listeners, guardar la proyección y marcar el evento como procesado debe ocurrir junto o no ocurrir.
//Diferencia con ms-clientes: no lleva @EnableMongoAuditing. Aquí las fechas vienen de los eventos o las pones tú, y Configuracion tendrá un _id fijo que complica distinguir un registro nuevo de uno existente.
