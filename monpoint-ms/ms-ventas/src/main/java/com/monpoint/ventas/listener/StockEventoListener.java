package com.monpoint.ventas.listener;

import com.monpoint.ventas.event.SobreEvento;
import com.monpoint.ventas.event.VentaEventPublisher;
import com.monpoint.ventas.event.VentaEventPublisher.LineaPublicada;
import com.monpoint.ventas.event.VentaEventPublisher.VentaPublicada;
import com.monpoint.ventas.event.VentasEventos;
import com.monpoint.ventas.event.entrada.StockRechazadoEvento;
import com.monpoint.ventas.event.entrada.StockReservadoEvento;
import com.monpoint.ventas.model.EstadoVenta;
import com.monpoint.ventas.model.EventoProcesado;
import com.monpoint.ventas.model.Venta;
import com.monpoint.ventas.repository.EventoProcesadoRepository;
import com.monpoint.ventas.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Resolución e idempotencia comparten transacción; el adaptador publica solo después del commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockEventoListener {
    private final JsonMapper jsonMapper;
    private final VentaRepository ventas;
    private final EventoProcesadoRepository eventosProcesados;
    private final VentaEventPublisher publicador;

    @Transactional
    @RabbitListener(queues = VentasEventos.COLA_STOCK)
    public void alRecibir(Message mensaje) {
        SobreEvento sobre = jsonMapper.readValue(mensaje.getBody(), SobreEvento.class);
        String eventoId = textoObligatorio(sobre.eventoId(), "eventoId");
        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=STOCK_DUPLICADO eventoId={}", eventoId);
            return;
        }

        String tipo = textoObligatorio(sobre.tipo(), "tipo");
        String ventaId;
        StockRechazadoEvento.Datos rechazo = null;
        switch (tipo) {
            case VentasEventos.TIPO_STOCK_RESERVADO -> {
                var evento = jsonMapper.convertValue(sobre, StockReservadoEvento.class);
                ventaId = Objects.requireNonNull(evento.datos(), "Los datos son obligatorios").ventaId();
            }
            case VentasEventos.TIPO_STOCK_RECHAZADO -> {
                var evento = jsonMapper.convertValue(sobre, StockRechazadoEvento.class);
                rechazo = Objects.requireNonNull(evento.datos(), "Los datos son obligatorios");
                ventaId = rechazo.ventaId();
            }
            default -> throw new IllegalArgumentException("Tipo de evento de stock desconocido: " + tipo);
        }
        textoObligatorio(ventaId, "ventaId");
        Venta venta = ventas.findById(ventaId)
                .orElseThrow(() -> new IllegalStateException("La venta del evento no existe: " + ventaId));
        // BSON guarda milisegundos: el evento y la venta deben anunciar exactamente la misma fecha.
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (venta.getEstado() != EstadoVenta.PENDIENTE) {
            eventosProcesados.save(EventoProcesado.de(eventoId, tipo, ahora));
            log.info("evento=STOCK_IGNORADO eventoId={} ventaId={} motivo=venta_resuelta", eventoId, ventaId);
            return;
        }

        if (rechazo == null) {
            venta.confirmar(ahora);
        } else {
            // motivoParaCajero valida que el productoId venga y que pertenezca a la venta; se llama
            // antes de leerlo para no depender del orden de evaluación de los argumentos.
            String motivo = motivoParaCajero(venta, rechazo);
            venta.cancelar(motivo, rechazo.productoId(), ahora);
        }
        ventas.save(venta);
        var salida = new VentaPublicada(venta.getId(), venta.getFolio(), venta.getClienteId(),
                venta.getClienteNombre(), venta.getTotal(), venta.getFechaCreacion(), venta.getFechaResolucion(),
                venta.getLineas().stream().map(l -> new LineaPublicada(l.productoId(), l.nombreProducto(),
                        l.cantidad(), l.precioUnitario())).toList());
        if (rechazo == null) publicador.publicarVentaConfirmada(salida);
        else publicador.publicarVentaCancelada(salida, rechazo.motivo());
        // Si este registro falla, también se revierten la venta y el callback afterCommit.
        eventosProcesados.save(EventoProcesado.de(eventoId, tipo, ahora));
        log.info("evento=VENTA_RESUELTA eventoId={} ventaId={} estado={}", eventoId, ventaId, venta.getEstado());
    }

    private static String motivoParaCajero(Venta venta, StockRechazadoEvento.Datos rechazo) {
        String productoId = textoObligatorio(rechazo.productoId(), "productoId");
        var lineas = venta.getLineas().stream().filter(l -> l.productoId().equals(productoId)).toList();
        if (lineas.isEmpty()) throw new IllegalArgumentException("El producto rechazado no pertenece a la venta");
        // Inventario agrupa productos repetidos: el solicitado debe sumar todas sus líneas congeladas.
        int solicitado = lineas.stream().mapToInt(l -> l.cantidad()).reduce(0, Math::addExact);
        String nombre = lineas.getFirst().nombreProducto();
        return switch (textoObligatorio(rechazo.motivo(), "motivo")) {
            case VentasEventos.MOTIVO_EXISTENCIA_INSUFICIENTE ->
                    "existencia insuficiente de " + nombre + ", solicitado " + solicitado;
            case VentasEventos.MOTIVO_PRODUCTO_INACTIVO -> "producto inactivo: " + nombre;
            case VentasEventos.MOTIVO_PRODUCTO_NO_ENCONTRADO -> "producto no disponible en inventario: " + nombre;
            default -> throw new IllegalArgumentException("Motivo de rechazo desconocido: " + rechazo.motivo());
        };
    }

    private static String textoObligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("El campo " + campo + " es obligatorio");
        return valor;
    }
}
