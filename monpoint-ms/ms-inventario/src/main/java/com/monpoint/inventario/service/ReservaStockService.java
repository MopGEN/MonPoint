package com.monpoint.inventario.service;

import com.monpoint.inventario.event.InventarioEventPublisher;
import com.monpoint.inventario.event.InventarioEventos;
import com.monpoint.inventario.event.LineaReservada;
import com.monpoint.inventario.event.VentaCreadaEvento;
import com.monpoint.inventario.model.EventoProcesado;
import com.monpoint.inventario.model.Producto;
import com.monpoint.inventario.repository.EventoProcesadoRepository;
import com.monpoint.inventario.repository.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reserva atómica de existencias al consumir {@code VentaCreada} (H-023, H-024; RF-2.4, RF-2.5, RF-2.7).
 * <p>
 * <b>Todas o ninguna:</b> las líneas se descuentan dentro de UNA transacción de MongoDB. Si alguna no se puede
 * (producto inexistente, inactivo o con menos existencia que la pedida), se marca la transacción para revertir:
 * no queda descontada ninguna línea, y se publica {@code StockRechazado}.
 * <p>
 * <b>Seguro ante concurrencia:</b> cada descuento es UNA operación condicional sobre el documento
 * ({@code existencia >= cantidad} y {@code activo}) con {@code $inc}; MongoDB la ejecuta de forma atómica, así
 * que dos ventas sobre el mismo producto nunca dejan la existencia negativa. Si dos transacciones chocan sobre
 * el mismo documento, MongoDB aborta una con un error transitorio y el reintento del listener la repite.
 * <p>
 * <b>Idempotente (RNF-16):</b> el {@code eventoId} se registra en {@code eventos_procesados} (índice único)
 * en la MISMA transacción que el descuento. Un evento reentregado se descarta sin efecto.
 * <p>
 * Los eventos se publican DESPUÉS de confirmar la transacción (outbox recortado, Doc 01 §7.4).
 * <p>
 * ⚠️ Limitación aceptada por el recorte del outbox: si el servicio cae entre el commit y la publicación,
 * la respuesta (StockReservado) no se emite y la reentrega ya se descarta por idempotente.
 */
@Slf4j
@Service
public class ReservaStockService {

    private final MongoTemplate mongoTemplate;
    private final ProductoRepository productoRepository;
    private final EventoProcesadoRepository eventosProcesados;
    private final InventarioEventPublisher eventPublisher;
    private final TransactionTemplate transaccion;

    public ReservaStockService(MongoTemplate mongoTemplate, ProductoRepository productoRepository,
                               EventoProcesadoRepository eventosProcesados,
                               InventarioEventPublisher eventPublisher,
                               PlatformTransactionManager transactionManager) {
        this.mongoTemplate = mongoTemplate;
        this.productoRepository = productoRepository;
        this.eventosProcesados = eventosProcesados;
        this.eventPublisher = eventPublisher;
        this.transaccion = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws IllegalArgumentException si el evento está mal formado (el listener lo reintenta y, al agotar los
     *                                  intentos, termina en la cola de fallidos)
     */
    public void procesarVentaCreada(VentaCreadaEvento evento) {
        validar(evento);
        String eventoId = evento.eventoId();
        String ventaId = evento.datos().ventaId();

        if (eventosProcesados.existsByEventoId(eventoId)) {
            log.info("evento=VENTA_CREADA_DESCARTADO eventoId={} motivo=ya_procesado", eventoId);
            return;
        }

        Map<String, Integer> cantidades = agruparPorProducto(evento);
        ResultadoReserva resultado = Objects.requireNonNull(
                transaccion.execute(status -> reservar(evento, cantidades, status)));

        if (resultado.reservada()) {
            log.info("evento=STOCK_RESERVADO eventoId={} ventaId={} lineas={}", eventoId, ventaId,
                    resultado.lineas().size());
            eventPublisher.publicarStockReservado(ventaId, resultado.lineas());
            // H-024: tras una reserva, igual o bajo el umbral ➔ StockBajo.
            for (LineaReservada linea : resultado.lineas()) {
                if (linea.existenciaResultante() <= linea.umbralStock()) {
                    eventPublisher.publicarStockBajo(linea);
                }
            }
        } else {
            log.info("evento=STOCK_RECHAZADO eventoId={} ventaId={} productoId={} motivo={}", eventoId, ventaId,
                    resultado.productoRechazado(), resultado.motivo());
            registrarProcesado(evento);
            eventPublisher.publicarStockRechazado(ventaId, resultado.productoRechazado(), resultado.motivo());
        }
    }

    /** Corre dentro de la transacción: registra el evento y descuenta cada línea, o marca revertir todo. */
    private ResultadoReserva reservar(VentaCreadaEvento evento, Map<String, Integer> cantidades,
                                      TransactionStatus status) {
        eventosProcesados.insert(EventoProcesado.de(evento.eventoId(), evento.tipo()));

        List<LineaReservada> reservadas = new ArrayList<>();
        for (Map.Entry<String, Integer> linea : cantidades.entrySet()) {
            String productoId = linea.getKey();
            int cantidad = linea.getValue();

            Query hayExistencia = Query.query(Criteria.where("_id").is(productoId)
                    .and("activo").is(true)
                    .and("existencia").gte(cantidad));
            Update descuento = new Update()
                    .inc("existencia", -cantidad)
                    .set("fechaActualizacion", Instant.now());
            Producto actualizado = mongoTemplate.findAndModify(hayExistencia, descuento,
                    FindAndModifyOptions.options().returnNew(true), Producto.class);

            if (actualizado == null) {
                String motivo = motivoDeRechazo(productoId);
                status.setRollbackOnly();
                return ResultadoReserva.rechazada(productoId, motivo);
            }
            reservadas.add(new LineaReservada(actualizado.getId(), actualizado.getNombre(), cantidad,
                    actualizado.getExistencia(), actualizado.getUmbralStock()));
        }
        return ResultadoReserva.reservada(reservadas);
    }

    private String motivoDeRechazo(String productoId) {
        return productoRepository.findById(productoId)
                .map(p -> p.isActivo()
                        ? InventarioEventos.MOTIVO_EXISTENCIA_INSUFICIENTE
                        : InventarioEventos.MOTIVO_PRODUCTO_INACTIVO)
                .orElse(InventarioEventos.MOTIVO_PRODUCTO_NO_ENCONTRADO);
    }

    /** Un rechazo no cambia nada en el inventario, pero el evento se anota para descartar su reentrega. */
    private void registrarProcesado(VentaCreadaEvento evento) {
        try {
            transaccion.executeWithoutResult(
                    status -> eventosProcesados.insert(EventoProcesado.de(evento.eventoId(), evento.tipo())));
        } catch (DuplicateKeyException ex) {
            log.debug("evento=VENTA_CREADA eventoId={} ya estaba registrado", evento.eventoId());
        }
    }

    /** Una venta puede repetir un producto en dos líneas: se suman para descontar una sola vez por producto. */
    private static Map<String, Integer> agruparPorProducto(VentaCreadaEvento evento) {
        Map<String, Integer> cantidades = new LinkedHashMap<>();
        for (VentaCreadaEvento.Linea linea : evento.datos().lineas()) {
            cantidades.merge(linea.productoId(), linea.cantidad(), Integer::sum);
        }
        return cantidades;
    }

    private static void validar(VentaCreadaEvento evento) {
        if (evento == null || evento.eventoId() == null || evento.eventoId().isBlank()) {
            throw new IllegalArgumentException("VentaCreada sin eventoId");
        }
        if (evento.datos() == null || evento.datos().lineas() == null || evento.datos().lineas().isEmpty()) {
            throw new IllegalArgumentException("VentaCreada sin líneas: eventoId=" + evento.eventoId());
        }
        for (VentaCreadaEvento.Linea linea : evento.datos().lineas()) {
            if (linea == null || linea.productoId() == null || linea.productoId().isBlank() || linea.cantidad() <= 0) {
                throw new IllegalArgumentException("VentaCreada con una línea inválida: eventoId=" + evento.eventoId());
            }
        }
    }
}
