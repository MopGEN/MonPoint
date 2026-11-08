package com.monpoint.reportes.listener;

import com.monpoint.reportes.event.ReportesEventos;
import com.monpoint.reportes.event.entrada.VentaCierreEvento;
import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.EventoProcesado;
import com.monpoint.reportes.model.LineaVenta;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import com.monpoint.reportes.repository.VentaProyeccionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Proyecta las ventas cerradas en {@code ventas_proy} (H-055). Aplica el evento y registra su id en una sola
 * transacción; cualquier error se propaga a Spring AMQP, que reintenta y al final manda el mensaje a
 * {@code reportes.ventas.fallidos}.
 * <p>
 * En ms-ventas CONFIRMADA y CANCELADA son estados finales que solo se alcanzan desde PENDIENTE: una venta
 * se cierra una sola vez. Por eso aquí no hay comparación de fechas como en productos y clientes; guardar
 * por {@code _id} basta para que una reentrega con otro eventoId no duplique la venta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaCierreListener {

    private final JsonMapper jsonMapper;
    private final VentaProyeccionRepository ventas;
    private final EventoProcesadoRepository eventosProcesados;
    private final Clock clock;

    @Transactional
    @RabbitListener(queues = ReportesEventos.COLA_VENTAS)
    public void alRecibir(Message mensaje) {
        VentaCierreEvento evento = jsonMapper.readValue(mensaje.getBody(), VentaCierreEvento.class);
        String eventoId = Objects.requireNonNull(evento.eventoId(), "El eventoId es obligatorio");
        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=VENTA_PROYECCION_DUPLICADO eventoId={}", eventoId);
            return;
        }

        EstadoVenta estado = estadoDe(evento.tipo());
        var datos = Objects.requireNonNull(evento.datos(), "Los datos del evento son obligatorios");
        String ventaId = Objects.requireNonNull(datos.ventaId(), "El ventaId es obligatorio");
        long folio = Objects.requireNonNull(datos.folio(), "El folio es obligatorio");
        Instant fecha = Instant.parse(
                Objects.requireNonNull(datos.fechaResolucion(), "La fecha de resolución es obligatoria"));
        List<LineaVenta> lineas = Objects.requireNonNull(datos.lineas(), "Las líneas son obligatorias").stream()
                .map(VentaCierreListener::lineaDe)
                .toList();

        ventas.save(VentaProyeccion.de(ventaId, folio, datos.clienteNombre(), estado, lineas,
                Objects.requireNonNull(datos.total(), "El total es obligatorio"), fecha));
        eventosProcesados.save(EventoProcesado.de(eventoId, evento.tipo(), Instant.now(clock)));
        log.info("evento=VENTA_PROYECCION_PROCESADO eventoId={} ventaId={} estado={}", eventoId, ventaId, estado);
    }

    /** Un tipo desconocido no es transitorio: falla y termina en la cola de fallidos para revisarlo. */
    private static EstadoVenta estadoDe(String tipo) {
        if (ReportesEventos.TIPO_VENTA_CONFIRMADA.equals(tipo)) {
            return EstadoVenta.CONFIRMADA;
        }
        if (ReportesEventos.TIPO_VENTA_CANCELADA.equals(tipo)) {
            return EstadoVenta.CANCELADA;
        }
        throw new IllegalArgumentException("Tipo de evento de venta no soportado: " + tipo);
    }

    private static LineaVenta lineaDe(VentaCierreEvento.Linea linea) {
        Objects.requireNonNull(linea, "La línea es obligatoria");
        int cantidad = Objects.requireNonNull(linea.cantidad(), "La cantidad de la línea es obligatoria");
        return LineaVenta.de(linea.productoId(), linea.nombreProducto(), linea.precioUnitario(), cantidad);
    }
}
