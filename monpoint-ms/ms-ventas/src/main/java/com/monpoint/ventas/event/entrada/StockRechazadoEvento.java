package com.monpoint.ventas.event.entrada;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta negativa de ms-inventario a VentaCreada. Consumirlo lleva la venta a CANCELADA (tarea 4.4).
 * <p>
 * {@code motivo} es uno de tres códigos: {@code PRODUCTO_NO_ENCONTRADO}, {@code PRODUCTO_INACTIVO} o
 * {@code EXISTENCIA_INSUFICIENTE} (ver {@code VentasEventos}).
 * <p>
 * <b>HUECO DE CONTRATO PENDIENTE CON VICTOR.</b> La tarea 5.2 exige que una venta cancelada muestre un motivo
 * en lenguaje comprensible, del tipo <i>«existencia insuficiente de Tinta para sello, disponible 1,
 * solicitado 3»</i>. Con lo que llega hoy se puede construir el nombre del producto (está en
 * productos_replica) y el solicitado (está en la venta), pero <b>no el disponible</b>: la existencia no se
 * replica, por diseño. Los campos {@code disponible} y {@code solicitado} ya están declarados aquí como
 * {@link Integer} para que, en cuanto ms-inventario los publique, lleguen sin tocar este archivo; mientras
 * tanto llegan nulos y el mensaje se degrada a la versión sin disponible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockRechazadoEvento(String eventoId, String tipo, String version, String fecha, String origen,
                                   Datos datos) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Datos(String ventaId, String productoId, String motivo, Integer disponible,
                        Integer solicitado) {
    }
}
