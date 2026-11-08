package com.monpoint.reportes.config;

import com.monpoint.reportes.event.ReportesEventos;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología que ms-reportes declara al arrancar (Spring AMQP la crea si no existe).
 * <p>
 * Los exchanges se declaran con los MISMOS atributos que sus publicadores (topic, durable, sin borrado
 * automático): si difieren, RabbitMQ rechaza la declaración y el servicio no arranca.
 * <p>
 * Cada cola tiene su cola de fallidos: al agotar los reintentos (application.properties), el mensaje
 * rechazado se enruta por el exchange por omisión {@code ""} a {@code <cola>.fallidos}.
 * Esta clase no define RabbitTemplate: ms-reportes solo consume, no publica.
 */
@Configuration
public class RabbitConfig {

    // ------------------------------------------------------------------ exchanges de los publicadores

    @Bean
    public TopicExchange ventasEventosExchange() {
        return new TopicExchange(ReportesEventos.EXCHANGE_VENTAS, true, false);
    }

    @Bean
    public TopicExchange inventarioEventosExchange() {
        return new TopicExchange(ReportesEventos.EXCHANGE_INVENTARIO, true, false);
    }

    @Bean
    public TopicExchange clientesEventosExchange() {
        return new TopicExchange(ReportesEventos.EXCHANGE_CLIENTES, true, false);
    }

    // ------------------------------------------------------------------ ventas cerradas

    @Bean
    public Queue colaVentas() {
        return colaConFallidos(ReportesEventos.COLA_VENTAS, ReportesEventos.COLA_VENTAS_FALLIDOS);
    }

    @Bean
    public Queue colaVentasFallidos() {
        return QueueBuilder.durable(ReportesEventos.COLA_VENTAS_FALLIDOS).build();
    }

    @Bean
    public Binding bindingVentaConfirmada() {
        return BindingBuilder.bind(colaVentas()).to(ventasEventosExchange())
                .with(ReportesEventos.RK_VENTA_CONFIRMADA);
    }

    @Bean
    public Binding bindingVentaCancelada() {
        return BindingBuilder.bind(colaVentas()).to(ventasEventosExchange())
                .with(ReportesEventos.RK_VENTA_CANCELADA);
    }

    // ------------------------------------------------------------------ catálogo de productos

    @Bean
    public Queue colaProductos() {
        return colaConFallidos(ReportesEventos.COLA_PRODUCTOS, ReportesEventos.COLA_PRODUCTOS_FALLIDOS);
    }

    @Bean
    public Queue colaProductosFallidos() {
        return QueueBuilder.durable(ReportesEventos.COLA_PRODUCTOS_FALLIDOS).build();
    }

    @Bean
    public Binding bindingProductos() {
        return BindingBuilder.bind(colaProductos()).to(inventarioEventosExchange())
                .with(ReportesEventos.RK_PRODUCTO_TODOS);
    }

    // ------------------------------------------------------------------ directorio de clientes

    @Bean
    public Queue colaClientes() {
        return colaConFallidos(ReportesEventos.COLA_CLIENTES, ReportesEventos.COLA_CLIENTES_FALLIDOS);
    }

    @Bean
    public Queue colaClientesFallidos() {
        return QueueBuilder.durable(ReportesEventos.COLA_CLIENTES_FALLIDOS).build();
    }

    @Bean
    public Binding bindingClientes() {
        return BindingBuilder.bind(colaClientes()).to(clientesEventosExchange())
                .with(ReportesEventos.RK_CLIENTE_TODOS);
    }

    private static Queue colaConFallidos(String cola, String colaFallidos) {
        return QueueBuilder.durable(cola)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", colaFallidos)
                .build();
    }
}
