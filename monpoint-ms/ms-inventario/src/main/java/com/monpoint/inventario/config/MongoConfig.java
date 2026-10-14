package com.monpoint.inventario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Auditoría ({@code @CreatedDate}/{@code @LastModifiedDate}) y transacciones.
 * <p>
 * Las transacciones solo funcionan porque MongoDB corre como replica set {@code rs0}.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
