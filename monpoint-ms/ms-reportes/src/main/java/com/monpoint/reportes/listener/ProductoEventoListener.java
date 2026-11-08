package com.monpoint.reportes.listener;

import com.monpoint.reportes.event.ReportesEventos;
import com.monpoint.reportes.event.entrada.ProductoEvento;
import com.monpoint.reportes.model.EventoProcesado;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import com.monpoint.reportes.repository.ProductoProyeccionRepository;
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

/**
 * Proyecta el catálogo en {@code productos_proy} (H-056). Mismo patrón que la réplica de ms-ventas: idempotente
 * por eventoId y descarta los eventos más viejos que lo guardado, porque RabbitMQ no garantiza el orden
 * entre reintentos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductoEventoListener {

    private final JsonMapper jsonMapper;
    private final ProductoProyeccionRepository productos;
    private final EventoProcesadoRepository eventosProcesados;
    private final Clock clock;

    @Transactional
    @RabbitListener(queues = ReportesEventos.COLA_PRODUCTOS)
    public void alRecibir(Message mensaje) {
        ProductoEvento evento = jsonMapper.readValue(mensaje.getBody(), ProductoEvento.class);
        String eventoId = Objects.requireNonNull(evento.eventoId(), "El eventoId es obligatorio");
        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=PRODUCTO_PROYECCION_DUPLICADO eventoId={}", eventoId);
            return;
        }

        var datos = Objects.requireNonNull(evento.datos(), "Los datos del evento son obligatorios");
        String id = Objects.requireNonNull(datos.productoId(), "El productoId es obligatorio");
        Objects.requireNonNull(datos.nombre(), "El nombre es obligatorio");
        Objects.requireNonNull(datos.precio(), "El precio es obligatorio");
        boolean activo = Objects.requireNonNull(datos.activo(), "El indicador activo es obligatorio");
        Instant fecha = datos.fechaActualizacion() == null ? null : Instant.parse(datos.fechaActualizacion());
        ProductoProyeccion producto = productos.findById(id).orElse(null);

        // Una fecha ausente no demuestra antigüedad. Solo se descartan fechas estrictamente anteriores.
        boolean anterior = producto != null && fecha != null && producto.getFechaActualizacion() != null
                && fecha.isBefore(producto.getFechaActualizacion());
        if (!anterior) {
            if (producto == null) {
                producto = ProductoProyeccion.crear(id, datos.nombre(), datos.precio(), activo, fecha);
            } else {
                producto.actualizarDesde(datos.nombre(), datos.precio(), activo, fecha);
            }
            productos.save(producto);
        }

        // Un evento antiguo también fue procesado: registrarlo evita repetir la decisión en cada reentrega.
        eventosProcesados.save(EventoProcesado.de(eventoId, evento.tipo(), Instant.now(clock)));
        log.info("evento=PRODUCTO_PROYECCION_PROCESADO eventoId={} resultado={}",
                eventoId, anterior ? "ANTIGUO" : "APLICADO");
    }
}
