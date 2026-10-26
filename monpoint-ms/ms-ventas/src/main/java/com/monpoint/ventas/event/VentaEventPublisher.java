package com.monpoint.ventas.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Publicación de los eventos que ms-ventas emite.
 * <p>
 * Es un puerto: la lógica de la venta depende de esta interfaz y no de RabbitMQ. El adaptador es
 * {@code impl.RabbitVentaEventPublisher}.
 * <p>
 * <b>Contrato de fallo: no lanza.</b> Si la publicación falla, se registra el error y la ejecución continúa.
 * Se invoca tras persistir, dentro de la transacción; el adaptador difiere el envío hasta afterCommit.
 * Cuando se intenta enviar, la venta <b>ya está persistida y la transacción ya confirmó</b>.
 * Propagar la excepción daría un 500 al cajero sobre una venta que sí existe, y el cliente web volvería a
 * cobrar. Es peor que un 202 sobre una venta que se quedará en PENDIENTE y que la consulta de pendientes
 * (RF-3.10) permite detectar.
 * <p>
 * Esa es la limitación aceptada por el recorte del outbox (Doc 01 §7.4), y la actividad 4.4 pide documentarla
 * en el cierre.
 */
public interface VentaEventPublisher {

    /**
     * Arranca la saga. ms-inventario tiene su cola {@code inventario.venta-creada} enlazada a la routing key
     * {@code venta.creada}; consume {@code ventaId} y, de cada línea, {@code productoId} y {@code cantidad}.
     * El resto de los campos los ignora, pero ms-reportes los necesitará.
     *
     * @param lineas al menos una; el orden se conserva
     */
    void publicarVentaCreada(String ventaId, long folio, String clienteId, String clienteNombre,
                             BigDecimal total, Instant fecha, List<LineaPublicada> lineas);

    /**
     * Cierra la saga en afirmativo. Consumidores: ms-reportes (proyección {@code ventas_proy}) y
     * ms-notificaciones.
     * <p>
     * Lleva la venta completa porque {@code ventas_proy} se construye solo con este evento: ms-reportes no
     * puede consultar a ms-ventas para completar lo que falte.
     */
    void publicarVentaConfirmada(VentaPublicada venta);

    /**
     * Cierra la saga en negativo. Mismo contenido que la confirmación, más el motivo.
     * <p>
     * El motivo viaja en el evento, pero <b>el texto que ve el cajero lo compone ms-ventas</b> y vive en la
     * venta ({@code motivoCancelacion}). Los consumidores reciben el código de ms-inventario, no la frase.
     *
     * @param motivo uno de los tres códigos de {@code VentasEventos.MOTIVO_*}
     */
    void publicarVentaCancelada(VentaPublicada venta, String motivo);

    /**
     * La venta completa tal como viaja en los eventos de cierre.
     * <p>
     * Los campos son exactamente los que {@code ventas_proy} necesita (Doc 04 §8.2), más {@code clienteId} y
     * {@code fechaCreacion}, que ms-notificaciones puede querer y que no cuesta nada incluir.
     * <p>
     * No lleva {@code estado}: lo determina el tipo del evento. Un mismo dato en dos sitios acaba
     * contradiciéndose.
     */
    record VentaPublicada(String ventaId, long folio, String clienteId, String clienteNombre, BigDecimal total,
                          Instant fechaCreacion, Instant fechaResolucion, List<LineaPublicada> lineas) {
    }

    /**
     * Una línea tal como viaja en el evento, con los valores <b>ya congelados</b> por ms-ventas.
     * <p>
     * {@code nombreProducto} y {@code precioUnitario} son la copia histórica del momento de la venta, no una
     * referencia viva al catálogo: un comprobante reimpreso dentro de seis meses tiene que mostrar lo que se
     * cobró, no lo que el producto cuesta hoy (Doc 02 §10.1).
     */
    record LineaPublicada(String productoId, String nombreProducto, int cantidad, BigDecimal precioUnitario) {
    }
}
