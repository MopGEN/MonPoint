package com.monpoint.ventas.config;

import com.monpoint.ventas.event.VentasEventos;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ de ms-ventas (Doc 03 §2.3, H-006, RNF-17).
 * <p>
 * Los tres exchanges se declaran con <b>exactamente</b> los mismos atributos que en ms-inventario y
 * ms-clientes ({@code durable = true}, {@code autoDelete = false}): declarar un exchange que ya existe es
 * inocuo mientras los atributos coincidan, pero con atributos distintos el broker rechaza la declaración y el
 * servicio no arranca.
 * <p>
 * <b>Tres colas, no una.</b> Separar la réplica del catálogo, la de clientes y las respuestas de la saga tiene
 * una razón operativa concreta: si un evento de producto con forma inesperada agota sus reintentos y envenena
 * su cola, la resolución de ventas —que es la ruta crítica— sigue fluyendo. Con una sola cola, un producto mal
 * formado detendría la saga.
 */
@Configuration
public class RabbitConfig {

    // ------------------------------------------------------------------
    // Exchanges
    // ------------------------------------------------------------------

    @Bean
    public TopicExchange ventasEventosExchange() {
        return new TopicExchange(VentasEventos.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange inventarioEventosExchange() {
        return new TopicExchange(VentasEventos.EXCHANGE_INVENTARIO, true, false);
    }

    @Bean
    public TopicExchange clientesEventosExchange() {
        return new TopicExchange(VentasEventos.EXCHANGE_CLIENTES, true, false);
    }

    // ------------------------------------------------------------------
    // Réplica del catálogo de productos
    // ------------------------------------------------------------------

    /**
     * Lo rechazado tras agotar los reintentos se reenvía por el exchange por omisión ({@code ""}) usando el
     * nombre de la cola de fallidos como routing key. Es el mismo mecanismo que usa ms-inventario.
     */
    @Bean
    public Queue colaProductos() {
        return QueueBuilder.durable(VentasEventos.COLA_PRODUCTOS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", VentasEventos.COLA_PRODUCTOS_FALLIDOS)
                .build();
    }

    @Bean
    public Queue colaProductosFallidos() {
        return QueueBuilder.durable(VentasEventos.COLA_PRODUCTOS_FALLIDOS).build();
    }

    @Bean
    public Binding bindingProductos() {
        return BindingBuilder.bind(colaProductos())
                .to(inventarioEventosExchange())
                .with(VentasEventos.RK_PRODUCTO_TODOS);
    }

    // ------------------------------------------------------------------
    // Réplica de clientes
    // ------------------------------------------------------------------

    @Bean
    public Queue colaClientes() {
        return QueueBuilder.durable(VentasEventos.COLA_CLIENTES)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", VentasEventos.COLA_CLIENTES_FALLIDOS)
                .build();
    }

    @Bean
    public Queue colaClientesFallidos() {
        return QueueBuilder.durable(VentasEventos.COLA_CLIENTES_FALLIDOS).build();
    }

    @Bean
    public Binding bindingClientes() {
        return BindingBuilder.bind(colaClientes())
                .to(clientesEventosExchange())
                .with(VentasEventos.RK_CLIENTE_TODOS);
    }

    // ------------------------------------------------------------------
    // Respuestas de la saga
    // ------------------------------------------------------------------

    @Bean
    public Queue colaStock() {
        return QueueBuilder.durable(VentasEventos.COLA_STOCK)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", VentasEventos.COLA_STOCK_FALLIDOS)
                .build();
    }

    @Bean
    public Queue colaStockFallidos() {
        return QueueBuilder.durable(VentasEventos.COLA_STOCK_FALLIDOS).build();
    }

    /**
     * Dos enlaces explícitos en lugar de {@code stock.*}. El patrón atraparía también {@code stock.bajo}, que
     * es de ms-notificaciones: ms-ventas lo recibiría, no sabría qué hacer con él y lo mandaría a su cola de
     * fallidos en cada aviso de umbral.
     */
    @Bean
    public Binding bindingStockReservado() {
        return BindingBuilder.bind(colaStock())
                .to(inventarioEventosExchange())
                .with(VentasEventos.RK_STOCK_RESERVADO);
    }

    @Bean
    public Binding bindingStockRechazado() {
        return BindingBuilder.bind(colaStock())
                .to(inventarioEventosExchange())
                .with(VentasEventos.RK_STOCK_RECHAZADO);
    }

    // ------------------------------------------------------------------
    // Publicación con confirmación
    // ------------------------------------------------------------------

    /**
     * {@code RabbitTemplate} con confirmaciones de publicación y devolución de mensajes no enrutables.
     * <p>
     * Sin esto, {@code send()} retorna cuando el mensaje se entregó al canal, <b>no</b> cuando el broker lo
     * aceptó: un mensaje con una routing key sin enlace se descarta en el broker sin lanzar ninguna excepción,
     * y la bitácora registraría una publicación que nunca ocurrió. En un diseño sin outbox —el outbox se
     * recortó del alcance, Doc 01 §7.4— la bitácora es la única red de seguridad, así que no puede mentir.
     * <p>
     * Requiere {@code spring.rabbitmq.publisher-confirm-type=correlated} y
     * {@code spring.rabbitmq.publisher-returns=true} en {@code application.properties}.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        template.setConfirmCallback((correlacion, aceptado, causa) -> {
            if (!aceptado) {
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class)
                        .error("evento=PUBLICACION_NO_CONFIRMADA correlacion={} causa={}",
                                correlacion != null ? correlacion.getId() : "-", causa);
            }
        });
        template.setReturnsCallback(devuelto ->
                org.slf4j.LoggerFactory.getLogger(RabbitConfig.class)
                        .error("evento=MENSAJE_NO_ENRUTABLE exchange={} routingKey={} respuesta={} motivo={}",
                                devuelto.getExchange(), devuelto.getRoutingKey(),
                                devuelto.getReplyCode(), devuelto.getReplyText()));
        return template;
    }
}
