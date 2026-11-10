package com.monpoint.notificaciones.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class RabbitConfig {
    public static final String COLA = "notificaciones.stock-bajo";
    public static final String FALLIDOS = COLA + ".fallidos";
    @Bean public TopicExchange inventarioEventos() { return new TopicExchange("inventario.eventos", true, false); }
    @Bean public Queue stockBajo() {
        return QueueBuilder.durable(COLA).deadLetterExchange("").deadLetterRoutingKey(FALLIDOS).build();
    }
    @Bean public Queue stockBajoFallidos() { return QueueBuilder.durable(FALLIDOS).build(); }
    @Bean public Binding stockBajoBinding() { return BindingBuilder.bind(stockBajo()).to(inventarioEventos()).with("stock.bajo"); }
}
