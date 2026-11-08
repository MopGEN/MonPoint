package com.monpoint.reportes.service;

import com.monpoint.reportes.dto.response.AgregadoVentasResponse;
import com.monpoint.reportes.dto.response.AgregadoVentasResponse.Punto;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.model.Periodo;
import com.monpoint.reportes.model.TotalPeriodo;
import com.monpoint.reportes.repository.VentaProyeccionRepository;
import com.monpoint.reportes.security.UsuarioActual;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Datos para las gráficas de ventas (H-054). Solo ADMIN.
 * <p>
 * {@code desde} y {@code hasta} son días completos e inclusivos en la zona del negocio: se convierten al rango
 * semiabierto {@code [desde 00:00, hasta+1 00:00)} para no perder ni duplicar la venta de las 23:59:59.
 * <p>
 * El constructor es explícito porque Lombok no copia {@code @Value} a los parámetros del constructor.
 */
@Service
public class AgregacionVentasService {

    /** Tope de puntos por consulta: p. ej. DIA cubre casi 3 años. Evita armar listas enormes por un rango absurdo. */
    static final int MAX_PUNTOS = 1000;

    private final VentaProyeccionRepository ventas;
    private final ZoneId zona;

    public AgregacionVentasService(VentaProyeccionRepository ventas,
                                   @Value("${monpoint.reportes.zona-horaria}") ZoneId zona) {
        this.ventas = ventas;
        this.zona = zona;
    }

    public AgregadoVentasResponse agregar(Periodo periodo, LocalDate desde, LocalDate hasta, UsuarioActual usuario) {
        if (!usuario.esAdmin()) {
            throw new AccesoDenegadoException("Solo un ADMIN puede consultar los reportes de ventas");
        }
        Objects.requireNonNull(periodo, "El periodo es obligatorio");
        if (hasta.isBefore(desde)) {
            throw new RangoInvalidoException("La fecha 'hasta' no puede ser anterior a 'desde'");
        }
        LocalDate primero = periodo.inicioDe(desde);
        LocalDate ultimo = periodo.inicioDe(hasta);
        if (periodo.periodosEntre(primero, ultimo) > MAX_PUNTOS) {
            throw new RangoInvalidoException("El rango genera más de " + MAX_PUNTOS
                    + " puntos; usa un periodo más grande o acorta las fechas");
        }

        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();
        Map<LocalDate, BigDecimal> totales = ventas
                .totalesPorPeriodo(inicio, fin, periodo.unidadMongo(), zona.getId()).stream()
                .collect(Collectors.toMap(fila -> LocalDate.ofInstant(fila.inicio(), zona), TotalPeriodo::suma,
                        BigDecimal::add));

        List<Punto> puntos = Stream.iterate(primero, fecha -> !fecha.isAfter(ultimo), periodo::siguiente)
                .map(fecha -> new Punto(fecha, totales.getOrDefault(fecha, BigDecimal.ZERO)))
                .toList();
        BigDecimal total = puntos.stream().map(Punto::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AgregadoVentasResponse(periodo, desde, hasta, zona.getId(), total, puntos);
    }
}
