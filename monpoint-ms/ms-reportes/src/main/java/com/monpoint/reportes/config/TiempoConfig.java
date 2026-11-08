package com.monpoint.reportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj del servicio. Se inyecta en lugar de llamar a {@code Instant.now()} para que las pruebas
 * puedan fijar la hora (fecha de actualización, fecha de generación de los documentos).
 */
@Configuration
public class TiempoConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
