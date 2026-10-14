package com.monpoint.inventario.event.impl;

import com.monpoint.inventario.event.InventarioEventPublisher;
import com.monpoint.inventario.event.InventarioEventos;
import com.monpoint.inventario.event.LineaReservada;
import com.monpoint.inventario.event.SobreEvento;
import com.monpoint.inventario.model.Producto;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publica a RabbitMQ con {@link RabbitTemplate} DESPUÉS de confirmar la transacción (outbox está recortado,
 * Doc 01 §7.4). Si no hay transacción activa, publica de inmediato.
 * <p>
 * Campos de {@code datos}: los de H-022 (identificador, nombre, precio, estado y fecha de actualización), H-023
 * (existencia resultante de cada producto; producto y motivo) y H-024 (identificador, nombre y existencia
 * resultante). ⚠️ Los nombres exactos son provisionales hasta el catálogo (H-008). {@code ventaId} no está en
 * el backlog, pero ms-ventas lo necesita para correlacionar la respuesta con su venta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitInventarioEventPublisher implements InventarioEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final JsonMapper jsonMapper;

    @Override
    public void publicarProductoCreado(Producto producto) {
        publicar(InventarioEventos.TIPO_PRODUCTO_CREADO, InventarioEventos.RK_PRODUCTO_CREADO, datosDe(producto));
    }

    @Override
    public void publicarProductoActualizado(Producto producto) {
        publicar(InventarioEventos.TIPO_PRODUCTO_ACTUALIZADO, InventarioEventos.RK_PRODUCTO_ACTUALIZADO,
                datosDe(producto));
    }

    @Override
    public void publicarProductoDadoDeBaja(Producto producto) {
        publicar(InventarioEventos.TIPO_PRODUCTO_DADO_DE_BAJA, InventarioEventos.RK_PRODUCTO_DADO_DE_BAJA,
                datosDe(producto));
    }

    @Override
    public void publicarStockReservado(String ventaId, List<LineaReservada> lineas) {
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (LineaReservada linea : lineas) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productoId", linea.productoId());
            item.put("cantidad", linea.cantidad());
            item.put("existenciaResultante", linea.existenciaResultante());
            detalle.add(item);
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ventaId", ventaId);
        datos.put("lineas", detalle);
        publicar(InventarioEventos.TIPO_STOCK_RESERVADO, InventarioEventos.RK_STOCK_RESERVADO, datos);
    }

    @Override
    public void publicarStockRechazado(String ventaId, String productoId, String motivo) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("ventaId", ventaId);
        datos.put("productoId", productoId);
        datos.put("motivo", motivo);
        publicar(InventarioEventos.TIPO_STOCK_RECHAZADO, InventarioEventos.RK_STOCK_RECHAZADO, datos);
    }

    @Override
    public void publicarStockBajo(LineaReservada linea) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("productoId", linea.productoId());
        datos.put("nombre", linea.nombre());
        datos.put("existenciaResultante", linea.existenciaResultante());
        publicar(InventarioEventos.TIPO_STOCK_BAJO, InventarioEventos.RK_STOCK_BAJO, datos);
    }

    private static Map<String, Object> datosDe(Producto producto) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("productoId", producto.getId());
        datos.put("nombre", producto.getNombre());
        datos.put("precio", producto.getPrecio());
        datos.put("activo", producto.isActivo());
        datos.put("fechaActualizacion",
                producto.getFechaActualizacion() != null ? producto.getFechaActualizacion().toString() : null);
        return datos;
    }

    private void publicar(String tipo, String routingKey, Map<String, Object> datos) {
        SobreEvento sobre = new SobreEvento(UUID.randomUUID().toString(), tipo, InventarioEventos.VERSION,
                Instant.now().toString(), InventarioEventos.ORIGEN, datos);
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

    /** Fail-safe: si el broker falla, se registra y el cambio de inventario ya está hecho. */
    private void enviar(SobreEvento sobre, String routingKey) {
        try {
            Message mensaje = MessageBuilder.withBody(jsonMapper.writeValueAsBytes(sobre))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(sobre.eventoId())
                    .build();
            rabbitTemplate.send(InventarioEventos.EXCHANGE, routingKey, mensaje);
            log.info("evento={} eventoId={} destino=rabbitmq exchange={} routingKey={}",
                    sobre.tipo(), sobre.eventoId(), InventarioEventos.EXCHANGE, routingKey);
        } catch (Exception ex) {
            log.error("evento={} eventoId={} NO se pudo publicar: {}", sobre.tipo(), sobre.eventoId(), ex.toString());
        }
    }
}
