package com.monpoint.clientes.event.impl;

import com.monpoint.clientes.event.ClienteEventPublisher;
import com.monpoint.clientes.event.ClienteEventos;
import com.monpoint.clientes.event.SobreEvento;
import com.monpoint.clientes.model.Cliente;
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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publica a RabbitMQ con {@link RabbitTemplate} DESPUÉS de confirmar la transacción (outbox está recortado,
 * Doc 01 §7.4). Si no hay transacción activa, publica de inmediato.
 * <p>
 * Los campos de {@code datos} salen del Doc 04: {@code clientes_replica} necesita id, nombre y fecha;
 * {@code contrapartes_proy} agrega correo, teléfono y dirección. ⚠️ Provisional hasta el catálogo (H-008).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitClienteEventPublisher implements ClienteEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publicarClienteCreado(Cliente cliente) {
        publicar(ClienteEventos.TIPO_CREADO, ClienteEventos.RK_CREADO, datosDe(cliente));
    }

    @Override
    public void publicarClienteActualizado(Cliente cliente) {
        publicar(ClienteEventos.TIPO_ACTUALIZADO, ClienteEventos.RK_ACTUALIZADO, datosDe(cliente));
    }

    private static Map<String, Object> datosDe(Cliente cliente) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("clienteId", cliente.getId());
        datos.put("nombre", cliente.getNombre());
        datos.put("correo", cliente.getCorreo());
        datos.put("telefono", cliente.getTelefono());
        datos.put("direccion", cliente.getDireccion());
        datos.put("fechaActualizacion",
                cliente.getFechaActualizacion() != null ? cliente.getFechaActualizacion().toString() : null);
        return datos;
    }

    private void publicar(String tipo, String routingKey, Map<String, Object> datos) {
        SobreEvento sobre = new SobreEvento(UUID.randomUUID().toString(), tipo, ClienteEventos.VERSION,
                Instant.now().toString(), ClienteEventos.ORIGEN, datos);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(sobre, routingKey);
                }
            });
        } else {
            enviar(sobre, routingKey);
        }
    }

    /** Fail-safe: si el broker falla, se registra y la operación del cliente ya está hecha. */
    private void enviar(SobreEvento sobre, String routingKey) {
        try {
            Message mensaje = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(sobre))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(sobre.eventoId())
                    .build();
            rabbitTemplate.send(ClienteEventos.EXCHANGE, routingKey, mensaje);
            log.info("evento={} eventoId={} destino=rabbitmq exchange={} routingKey={}",
                    sobre.tipo(), sobre.eventoId(), ClienteEventos.EXCHANGE, routingKey);
        } catch (Exception ex) {
            log.error("evento={} eventoId={} NO se pudo publicar: {}", sobre.tipo(), sobre.eventoId(), ex.toString());
        }
    }
}
