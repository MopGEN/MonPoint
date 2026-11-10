package com.monpoint.notificaciones.listener;

import com.monpoint.notificaciones.config.RabbitConfig;
import com.monpoint.notificaciones.event.StockBajo;
import com.monpoint.notificaciones.service.NotificacionService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
@Component
@RequiredArgsConstructor
public class StockBajoListener {
    private final JsonMapper json;
    private final NotificacionService servicio;
    @RabbitListener(queues = RabbitConfig.COLA)
    public void recibir(Message mensaje) {
        StockBajo evento = json.readValue(mensaje.getBody(), StockBajo.class);
        String messageId = mensaje.getMessageProperties().getMessageId();
        if (evento == null || (messageId != null && !messageId.equals(evento.eventoId())))
            throw new IllegalArgumentException("messageId distinto de eventoId");
        servicio.recibir(evento);
    }
}
