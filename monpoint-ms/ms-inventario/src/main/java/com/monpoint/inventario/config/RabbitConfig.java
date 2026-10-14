package com.monpoint.inventario.config;

import com.monpoint.inventario.event.InventarioEventos;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ que le toca a ms-inventario (Doc 03 §2.3, H-006, RNF-17). Spring AMQP la declara al
 * abrir la primera conexión; si ya existe con los mismos atributos, no pasa nada.
 * <ul>
 *   <li>Su exchange {@code inventario.eventos} (topic), donde publica.</li>
 *   <li>El exchange {@code ventas.eventos} (topic) lo declara también ms-ventas; declararlo igual en ambos es inocuo.</li>
 *   <li>Su cola de VentaCreada, con una cola de mensajes fallidos: tras agotar los reintentos (ver
 *       {@code spring.rabbitmq.listener.simple.retry.*}), el mensaje rechazado va a la cola de fallidos.</li>
 * </ul>
 * ⚠️ Nombres y routing key provisionales hasta el catálogo de eventos (H-008).
 */
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange inventarioEventosExchange() {
        return new TopicExchange(InventarioEventos.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange ventasEventosExchange() {
        return new TopicExchange(InventarioEventos.EXCHANGE_VENTAS, true, false);
    }

    /** Cola de VentaCreada: lo rechazado se reenvía (exchange por omisión) a la cola de fallidos. */
    @Bean
    public Queue colaVentaCreada() {
        return QueueBuilder.durable(InventarioEventos.COLA_VENTA_CREADA)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", InventarioEventos.COLA_VENTA_CREADA_FALLIDOS)
                .build();
    }

    @Bean
    public Queue colaVentaCreadaFallidos() {
        return QueueBuilder.durable(InventarioEventos.COLA_VENTA_CREADA_FALLIDOS).build();
    }

    @Bean
    public Binding bindingVentaCreada() {
        return BindingBuilder.bind(colaVentaCreada())
                .to(ventasEventosExchange())
                .with(InventarioEventos.RK_VENTA_CREADA);
    }
}
