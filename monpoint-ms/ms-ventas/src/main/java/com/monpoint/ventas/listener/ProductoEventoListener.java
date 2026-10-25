package com.monpoint.ventas.listener;

import com.monpoint.ventas.event.VentasEventos;
import com.monpoint.ventas.event.entrada.ProductoEvento;
import com.monpoint.ventas.model.ProductoReplica;
import com.monpoint.ventas.model.EventoProcesado;
import com.monpoint.ventas.repository.ProductoReplicaRepository;
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
public class ProductoEventoListener {

    private final JsonMapper jsonMapper;
    private final ProductoReplicaRepository replicas;
    private final EventoProcesadoRepository eventosProcesados;

    @Transactional
    @RabbitListener(queues = VentasEventos.COLA_PRODUCTOS)
    public void alRecibir(Message mensaje) {
        ProductoEvento evento = jsonMapper.readValue(mensaje.getBody(), ProductoEvento.class);
        String eventoId = Objects.requireNonNull(evento.eventoId(), "El eventoId es obligatorio");
        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=PRODUCTO_REPLICA_DUPLICADO eventoId={}", eventoId);
            return;
        }

        var datos = Objects.requireNonNull(evento.datos(), "Los datos del evento son obligatorios");
        String id = Objects.requireNonNull(datos.productoId(), "El productoId es obligatorio");
        Objects.requireNonNull(datos.nombre(), "El nombre es obligatorio");
        Objects.requireNonNull(datos.precio(), "El precio es obligatorio");
        Objects.requireNonNull(datos.activo(), "El campo activo es obligatorio");
        Instant fecha = datos.fechaActualizacion() == null ? null : Instant.parse(datos.fechaActualizacion());
        ProductoReplica replica = replicas.findById(id).orElse(null);

        // Una fecha ausente no demuestra antigüedad. Solo se descartan fechas estrictamente anteriores.
        boolean anterior = replica != null && fecha != null && replica.getFechaActualizacion() != null
                && fecha.isBefore(replica.getFechaActualizacion());
        if (!anterior) {
            if (replica == null) {
                replica = ProductoReplica.crear(id, datos.nombre(), datos.precio(), datos.activo(), fecha);
            } else {
                replica.actualizarDesde(datos.nombre(), datos.precio(), datos.activo(), fecha);
            }
            replicas.save(replica);
        }

        // Un evento antiguo también fue procesado: registrar evita repetir la decisión en cada reentrega.
        eventosProcesados.save(EventoProcesado.de(eventoId, evento.tipo(), Instant.now()));
        log.info("evento=PRODUCTO_REPLICA_PROCESADO eventoId={} resultado={}",
                eventoId, anterior ? "ANTIGUO" : "APLICADO");
    }
}
