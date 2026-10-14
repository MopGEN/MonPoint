package com.monpoint.clientes.config;

import com.monpoint.clientes.event.ClienteEventos;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ que le toca a ms-clientes: su exchange de tipo topic (Doc 03 §2.3, H-006).
 * Spring AMQP lo declara al abrir la primera conexión; si ya existe con los mismos atributos, no pasa nada.
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange clientesEventosExchange() {
        return new TopicExchange(ClienteEventos.EXCHANGE, true, false);
    }
}
