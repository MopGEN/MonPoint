package com.monpoint.ventas.event.impl;

import com.monpoint.ventas.event.SobreEvento;
import com.monpoint.ventas.event.VentaEventPublisher;
import com.monpoint.ventas.event.VentasEventos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adaptador de RabbitMQ para los eventos de ms-ventas.
 * <p>
 * <b>Publica después del commit, no dentro de la transacción.</b> Si se publicara dentro y la transacción se
 * revirtiera, ms-inventario reservaría existencias para una venta que no existe y nadie resolvería nunca esa
 * reserva. El orden correcto es: confirmar la venta en la base, y solo entonces anunciarla.
 * <p>
 * La contrapartida es la ventana sin outbox: si el proceso cae entre el commit y el envío, la venta queda
 * PENDIENTE y el evento no sale. Es la limitación que documenta la actividad 4.4.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitVentaEventPublisher implements VentaEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publicarVentaCreada(String ventaId, long folio, String clienteId, String clienteNombre,
                                    BigDecimal total, Instant fecha, List<LineaPublicada> lineas) {
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (LineaPublicada linea : lineas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productoId", linea.productoId());
            item.put("nombreProducto", linea.nombreProducto());
            item.put("cantidad", linea.cantidad());
            item.put("precioUnitario", linea.precioUnitario());
            detalle.add(item);
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        // ventaId y lineas[productoId, cantidad] son lo unico que lee ms-inventario.
        datos.put("ventaId", ventaId);
        datos.put("folio", folio);
        datos.put("clienteId", clienteId);
        datos.put("clienteNombre", clienteNombre);
        datos.put("total", total);
        datos.put("fecha", fecha != null ? fecha.toString() : null);
        datos.put("lineas", detalle);

        publicar(VentasEventos.TIPO_VENTA_CREADA, VentasEventos.RK_VENTA_CREADA, datos);
    }

    // ------------------------------------------------------------------

    private void publicar(String tipo, String routingKey, Map<String, Object> datos) {
        SobreEvento sobre = new SobreEvento(UUID.randomUUID().toString(), tipo, VentasEventos.VERSION,
                Instant.now().toString(), VentasEventos.ORIGEN, datos);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(sobre, routingKey);
                }
            });
        } else {
            // Sin transacción activa (pruebas, o una invocación fuera de la venta): se envía de inmediato.
            enviar(sobre, routingKey);
        }
    }

    /**
     * No lanza: ver el contrato en {@link VentaEventPublisher}. La venta ya está confirmada cuando esto corre.
     * <p>
     * El acuse del broker no llega aquí sino al {@code ConfirmCallback} y al {@code ReturnsCallback} de
     * {@code RabbitConfig}: un mensaje sin enlace a ninguna cola se registra allí como
     * {@code MENSAJE_NO_ENRUTABLE}. Por eso esta bitácora dice «enviado», no «entregado».
     */
    private void enviar(SobreEvento sobre, String routingKey) {
        try {
            byte[] cuerpo = jsonMapper.writeValueAsBytes(sobre);
            Message mensaje = MessageBuilder.withBody(cuerpo)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding(StandardCharsets.UTF_8.name())
                    .setMessageId(sobre.eventoId())
                    .build();
            rabbitTemplate.send(VentasEventos.EXCHANGE, routingKey, mensaje);
            log.info("evento={} eventoId={} enviado=exchange:{} routingKey={}",
                    sobre.tipo(), sobre.eventoId(), VentasEventos.EXCHANGE, routingKey);
        } catch (Exception ex) {
            // La venta esta persistida y confirmada. Se registra con todo lo necesario para reenviarla a mano
            // y se continua: fallar aqui daria un 500 sobre una venta que si existe.
            log.error("evento={} eventoId={} ventaId={} folio={} NO se pudo publicar. La venta queda PENDIENTE y hay que "
                            + "reenviarla o cancelarla a mano (RF-3.10). routingKey={} causa={}",
                    sobre.tipo(), sobre.eventoId(), sobre.datos().get("ventaId"), sobre.datos().get("folio"),
                    routingKey, ex.toString(), ex);
        }
    }
}
