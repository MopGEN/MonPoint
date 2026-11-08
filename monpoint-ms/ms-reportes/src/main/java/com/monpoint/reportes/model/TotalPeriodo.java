package com.monpoint.reportes.model;

import org.springframework.data.annotation.Id;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fila que devuelve la agregación de {@code ventas_proy}: el {@code _id} del {@code $group} es el instante en que
 * empieza el periodo (medianoche local, expresada en UTC) y {@code suma} el total de ese periodo, leído de
 * Decimal128 sin pérdida.
 */
public record TotalPeriodo(@Id Instant inicio, BigDecimal suma) {
}
