package com.monpoint.inventario.service;

import com.monpoint.inventario.event.LineaReservada;

import java.util.List;

/** Resultado de intentar reservar todas las líneas de una venta: o se reservó todo, o no se tocó nada. */
public record ResultadoReserva(boolean reservada, List<LineaReservada> lineas, String productoRechazado,
                               String motivo) {

    public static ResultadoReserva reservada(List<LineaReservada> lineas) {
        return new ResultadoReserva(true, lineas, null, null);
    }

    public static ResultadoReserva rechazada(String productoId, String motivo) {
        return new ResultadoReserva(false, List.of(), productoId, motivo);
    }
}
