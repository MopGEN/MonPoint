package com.monpoint.inventario.event;

import com.monpoint.inventario.service.ReservaStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de {@code VentaCreada} (H-023). Convierte el mensaje y delega en {@link ReservaStockService}.
 * <p>
 * Cualquier excepción se propaga a propósito: el reintento de Spring AMQP lo vuelve a intentar y, al agotar
 * los intentos, el mensaje va a la cola de fallidos (RNF-17). El rechazo por falta de existencia NO es una
 * excepción: es un resultado de negocio que se responde con {@code StockRechazado}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaCreadaListener {

    private final JsonMapper jsonMapper;
    private final ReservaStockService reservaStockService;

    @RabbitListener(queues = InventarioEventos.COLA_VENTA_CREADA)
    public void alRecibir(Message mensaje) {
        VentaCreadaEvento evento = jsonMapper.readValue(mensaje.getBody(), VentaCreadaEvento.class);
        log.info("evento=VENTA_CREADA_RECIBIDO eventoId={}", evento.eventoId());
        reservaStockService.procesarVentaCreada(evento);
    }
}
