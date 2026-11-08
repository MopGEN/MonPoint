package com.monpoint.reportes.dto.response;

import com.monpoint.reportes.model.Periodo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Serie lista para graficar: un punto por periodo, en orden y sin huecos (los periodos sin ventas valen 0).
 * {@code inicio} es el primer día del periodo en la zona horaria del negocio; el primer y el último punto pueden
 * cubrir solo una parte del periodo si el rango no empieza o termina en su borde.
 */
public record AgregadoVentasResponse(Periodo periodo, LocalDate desde, LocalDate hasta, String zonaHoraria,
                                     BigDecimal total, List<Punto> puntos) {

    public record Punto(LocalDate inicio, BigDecimal total) {
    }
}
