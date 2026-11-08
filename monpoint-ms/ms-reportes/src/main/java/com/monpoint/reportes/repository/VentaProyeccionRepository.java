package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.TotalPeriodo;
import com.monpoint.reportes.model.VentaProyeccion;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;

public interface VentaProyeccionRepository extends MongoRepository<VentaProyeccion, String> {

    /**
     * Suma las ventas CONFIRMADAS de {@code [desde, hasta)} agrupadas por periodo (H-054).
     * <p>
     * El {@code $match} usa el índice {@code (fecha, estado)}. {@code $dateTrunc} corta cada fecha en la zona
     * horaria del negocio, así una venta de las 23:30 en Ciudad de México cae en su día local y no en el
     * siguiente día UTC. {@code $sum} sobre Decimal128 conserva los centavos exactos.
     * <p>
     * La salida se llama {@code suma} y no {@code total} a propósito: Spring Data mapea el pipeline contra
     * {@link VentaProyeccion}, y una clave {@code total} chocaría con su campo DECIMAL128 e intentaría convertir
     * el texto {@code '$total'} a número.
     *
     * @param unidad {@code day}, {@code month} o {@code year}
     * @param zona   identificador IANA, p. ej. {@code America/Mexico_City}
     */
    @Aggregation(pipeline = {
            "{ $match: { estado: 'CONFIRMADA', fecha: { $gte: ?0, $lt: ?1 } } }",
            "{ $group: { _id: { $dateTrunc: { date: '$fecha', unit: ?2, timezone: ?3 } }, suma: { $sum: '$total' } } }"
    })
    List<TotalPeriodo> totalesPorPeriodo(Instant desde, Instant hasta, String unidad, String zona);

    /**
     * Ventas CONFIRMADAS de {@code [desde, hasta)} en orden cronológico, para el historial en PDF (H-052).
     * Usa el mismo índice {@code (fecha, estado)} que la agregación.
     */
    @Query(value = "{ estado: 'CONFIRMADA', fecha: { $gte: ?0, $lt: ?1 } }", sort = "{ fecha: 1, folio: 1 }")
    List<VentaProyeccion> confirmadasEntre(Instant desde, Instant hasta);
}
