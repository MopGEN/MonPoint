package com.monpoint.ventas.listener;

import com.monpoint.ventas.event.VentasEventos;
import com.monpoint.ventas.event.entrada.ClienteEvento;
import com.monpoint.ventas.model.ClienteReplica;
import com.monpoint.ventas.model.EventoProcesado;
import com.monpoint.ventas.repository.ClienteReplicaRepository;
import com.monpoint.ventas.repository.EventoProcesadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Objects;

/** Aplica el evento y registra su id en una sola transacción; los errores se propagan a Spring AMQP. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClienteEventoListener {

    private final JsonMapper jsonMapper;
    private final ClienteReplicaRepository replicas;
    private final EventoProcesadoRepository eventosProcesados;

    @Transactional
    @RabbitListener(queues = VentasEventos.COLA_CLIENTES)
    public void alRecibir(Message mensaje) {
        ClienteEvento evento = jsonMapper.readValue(mensaje.getBody(), ClienteEvento.class);
        String eventoId = Objects.requireNonNull(evento.eventoId(), "El eventoId es obligatorio");
        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=CLIENTE_REPLICA_DUPLICADO eventoId={}", eventoId);
            return;
        }

        var datos = Objects.requireNonNull(evento.datos(), "Los datos del evento son obligatorios");
        String id = Objects.requireNonNull(datos.clienteId(), "El clienteId es obligatorio");
        Objects.requireNonNull(datos.nombre(), "El nombre es obligatorio");
        Instant fecha = datos.fechaActualizacion() == null ? null : Instant.parse(datos.fechaActualizacion());
        ClienteReplica replica = replicas.findById(id).orElse(null);

        // Una fecha ausente no demuestra antigüedad. Solo se descartan fechas estrictamente anteriores.
        boolean anterior = replica != null && fecha != null && replica.getFechaActualizacion() != null
                && fecha.isBefore(replica.getFechaActualizacion());
        if (!anterior) {
            if (replica == null) {
                replica = ClienteReplica.crear(id, datos.nombre(), fecha);
            } else {
                replica.actualizarDesde(datos.nombre(), fecha);
            }
            replicas.save(replica);
        }

        // Un evento antiguo también fue procesado: registrar evita repetir la decisión en cada reentrega.
        eventosProcesados.save(EventoProcesado.de(eventoId, evento.tipo(), Instant.now()));
        log.info("evento=CLIENTE_REPLICA_PROCESADO eventoId={} resultado={}",
                eventoId, anterior ? "ANTIGUO" : "APLICADO");
    }
}
