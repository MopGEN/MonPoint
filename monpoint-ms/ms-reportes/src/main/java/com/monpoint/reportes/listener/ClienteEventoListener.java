package com.monpoint.reportes.listener;

import com.monpoint.reportes.event.ReportesEventos;
import com.monpoint.reportes.event.entrada.ClienteEvento;
import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.EventoProcesado;
import com.monpoint.reportes.repository.ContraparteProyeccionRepository;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Proyecta los clientes en {@code contrapartes_proy} con {@code tipo = CLIENTE} (H-056). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClienteEventoListener {

    private final JsonMapper jsonMapper;
    private final ContraparteProyeccionRepository contrapartes;
    private final EventoProcesadoRepository eventosProcesados;
    private final Clock clock;

    @Transactional
    @RabbitListener(queues = ReportesEventos.COLA_CLIENTES)
    public void alRecibir(Message mensaje) {
        ClienteEvento evento = jsonMapper.readValue(mensaje.getBody(), ClienteEvento.class);
        String eventoId = Objects.requireNonNull(evento.eventoId(), "El eventoId es obligatorio");
        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=CLIENTE_PROYECCION_DUPLICADO eventoId={}", eventoId);
            return;
        }

        var datos = Objects.requireNonNull(evento.datos(), "Los datos del evento son obligatorios");
        String id = Objects.requireNonNull(datos.clienteId(), "El clienteId es obligatorio");
        Objects.requireNonNull(datos.nombre(), "El nombre es obligatorio");
        Instant fecha = datos.fechaActualizacion() == null ? null : Instant.parse(datos.fechaActualizacion());
        ContraparteProyeccion cliente = contrapartes.findById(id).orElse(null);

        // Una fecha ausente no demuestra antigüedad. Solo se descartan fechas estrictamente anteriores.
        boolean anterior = cliente != null && fecha != null && cliente.getFechaActualizacion() != null
                && fecha.isBefore(cliente.getFechaActualizacion());
        if (!anterior) {
            if (cliente == null) {
                cliente = ContraparteProyeccion.cliente(id, datos.nombre(), datos.correo(), datos.telefono(),
                        datos.direccion(), fecha);
            } else {
                cliente.actualizarDesde(datos.nombre(), datos.correo(), datos.telefono(), datos.direccion(), fecha);
            }
            contrapartes.save(cliente);
        }

        eventosProcesados.save(EventoProcesado.de(eventoId, evento.tipo(), Instant.now(clock)));
        log.info("evento=CLIENTE_PROYECCION_PROCESADO eventoId={} resultado={}",
                eventoId, anterior ? "ANTIGUO" : "APLICADO");
    }
}
