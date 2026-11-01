package com.monpoint.ventas.dto.response;

import com.monpoint.ventas.model.EstadoVenta;
import com.monpoint.ventas.model.Venta;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Vista de lectura de una venta (actividad 5.2).
 *
 * <p><b>Por qué no se reutiliza {@link VentaResponse}.</b> Aquel es el acuse del registro: confirma que la
 * venta se aceptó y devuelve lo mínimo para identificarla ({@code id}, {@code folio}, {@code estado}).
 * Este es la proyección de lectura. Separarlos deja que cada uno evolucione sin romper al otro: agregar un
 * campo aquí no cambia la respuesta 202 que el cliente web ya consume.
 *
 * <p><b>El subtotal y el total se calculan en el servidor a propósito.</b> El Documento 04 prohíbe
 * persistir el subtotal para no tener dos fuentes de verdad, pero eso no obliga a que lo calcule el
 * navegador. JavaScript no tiene decimales exactos: los números son IEEE-754 de doble precisión, y
 * {@code 41.15 * 3} da {@code 123.44999999999999}. Si la caja multiplicara en el cliente, el importe
 * mostrado podría diferir del cobrado. Aquí se calcula con {@link BigDecimal} y el cliente solo lo
 * muestra; <b>el cliente nunca debe recalcular importes.</b>
 *
 * <p><b>{@code motivo} llega redactado, no como código.</b> {@code StockEventoListener} ya traduce el
 * código del evento ({@code EXISTENCIA_INSUFICIENTE} y los otros dos) a una frase de caja antes de
 * guardarla, de modo que aquí viaja algo como «existencia insuficiente de Tinta para sello, solicitado 3».
 * El cliente lo muestra tal cual; no tiene que traducir nada.
 *
 * <p>Los campos que no aplican viajan en {@code null} en lugar de omitirse: {@code motivo} salvo en
 * CANCELADA, {@code fechaResolucion} mientras está PENDIENTE, y los dos de cliente en una venta sin
 * cliente. Así la forma del objeto es siempre la misma y el cliente no necesita comprobar la existencia
 * de cada campo antes de leerlo.
 */
public record VentaDetalleResponse(String id,
                                   long folio,
                                   EstadoVenta estado,
                                   String clienteId,
                                   String clienteNombre,
                                   List<LineaDetalle> lineas,
                                   BigDecimal total,
                                   String motivo,
                                   Instant fechaCreacion,
                                   Instant fechaResolucion) {

    /** Línea con sus valores congelados en el momento de la venta, más el subtotal derivado. */
    public record LineaDetalle(String productoId,
                               String nombreProducto,
                               BigDecimal precioUnitario,
                               int cantidad,
                               BigDecimal subtotal) {
    }

    public static VentaDetalleResponse de(Venta venta) {
        List<LineaDetalle> lineas = venta.getLineas().stream()
                .map(l -> new LineaDetalle(l.productoId(), l.nombreProducto(), l.precioUnitario(),
                        l.cantidad(), l.subtotal()))
                .toList();
        return new VentaDetalleResponse(venta.getId(), venta.getFolio(), venta.getEstado(),
                venta.getClienteId(), venta.getClienteNombre(), lineas, venta.getTotal(),
                venta.getMotivoCancelacion(), venta.getFechaCreacion(), venta.getFechaResolucion());
    }
}
