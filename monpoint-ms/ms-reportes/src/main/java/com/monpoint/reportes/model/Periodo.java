package com.monpoint.reportes.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Granularidad de las gráficas (H-054). {@code unidadMongo} es el valor que espera {@code $dateTrunc};
 * {@code inicioDe} y {@code siguiente} recorren los mismos periodos en Java para rellenar los vacíos con 0.
 */
public enum Periodo {
    DIA("day", ChronoUnit.DAYS),
    MES("month", ChronoUnit.MONTHS),
    ANIO("year", ChronoUnit.YEARS);

    private final String unidadMongo;
    private final ChronoUnit unidad;

    Periodo(String unidadMongo, ChronoUnit unidad) {
        this.unidadMongo = unidadMongo;
        this.unidad = unidad;
    }

    public String unidadMongo() {
        return unidadMongo;
    }

    /** Primer día del periodo que contiene a {@code fecha}. */
    public LocalDate inicioDe(LocalDate fecha) {
        return switch (this) {
            case DIA -> fecha;
            case MES -> fecha.withDayOfMonth(1);
            case ANIO -> fecha.withDayOfYear(1);
        };
    }

    public LocalDate siguiente(LocalDate inicio) {
        return inicio.plus(1, unidad);
    }

    /** Cuántos periodos hay entre dos inicios, contando ambos. */
    public long periodosEntre(LocalDate primerInicio, LocalDate ultimoInicio) {
        return unidad.between(primerInicio, ultimoInicio) + 1;
    }
}
