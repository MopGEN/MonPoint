package com.monpoint.reportes.service;

import com.monpoint.reportes.dto.response.AgregadoVentasResponse;
import com.monpoint.reportes.dto.response.AgregadoVentasResponse.Punto;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.model.Periodo;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.model.TotalPeriodo;
import com.monpoint.reportes.repository.VentaProyeccionRepository;
import com.monpoint.reportes.security.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgregacionVentasServiceTest {

    private static final ZoneId CDMX = ZoneId.of("America/Mexico_City");
    private static final UsuarioActual ADMIN = new UsuarioActual("a1", Rol.ADMIN);
    private static final UsuarioActual VENDEDOR = new UsuarioActual("v1", Rol.VENDEDOR);

    @Mock VentaProyeccionRepository ventas;
    private AgregacionVentasService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AgregacionVentasService(ventas, CDMX);
    }

    @Test
    void vendedorNoPuedeConsultar() {
        assertThatThrownBy(() -> servicio.agregar(Periodo.DIA, dia(1), dia(3), VENDEDOR))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(ventas);
    }

    @Test
    void rangoInvertidoEsInvalido() {
        assertThatThrownBy(() -> servicio.agregar(Periodo.DIA, dia(3), dia(1), ADMIN))
                .isInstanceOf(RangoInvalidoException.class);
        verifyNoInteractions(ventas);
    }

    @Test
    void demasiadosPuntosEsInvalido() {
        LocalDate desde = LocalDate.of(2020, 1, 1);
        LocalDate hasta = desde.plusDays(AgregacionVentasService.MAX_PUNTOS);

        assertThatThrownBy(() -> servicio.agregar(Periodo.DIA, desde, hasta, ADMIN))
                .isInstanceOf(RangoInvalidoException.class)
                .hasMessageContaining(String.valueOf(AgregacionVentasService.MAX_PUNTOS));
        verifyNoInteractions(ventas);
    }

    @Test
    void elTopeExactoSiSePermite() {
        LocalDate desde = LocalDate.of(2020, 1, 1);
        LocalDate hasta = desde.plusDays(AgregacionVentasService.MAX_PUNTOS - 1);
        when(ventas.totalesPorPeriodo(any(), any(), anyString(), anyString())).thenReturn(List.of());

        assertThat(servicio.agregar(Periodo.DIA, desde, hasta, ADMIN).puntos())
                .hasSize(AgregacionVentasService.MAX_PUNTOS);
    }

    @Test
    void diasCompletosSeConviertenARangoSemiabiertoEnLaZonaDelNegocio() {
        when(ventas.totalesPorPeriodo(any(), any(), anyString(), anyString())).thenReturn(List.of());

        servicio.agregar(Periodo.DIA, dia(1), dia(3), ADMIN);

        // Ciudad de México es UTC-6: la medianoche local del día 1 son las 06:00 UTC.
        verify(ventas).totalesPorPeriodo(Instant.parse("2026-11-01T06:00:00Z"),
                Instant.parse("2026-11-04T06:00:00Z"), "day", "America/Mexico_City");
    }

    @Test
    void periodosSinVentasSeRellenanConCero() {
        when(ventas.totalesPorPeriodo(any(), any(), anyString(), anyString())).thenReturn(List.of(
                new TotalPeriodo(Instant.parse("2026-11-02T06:00:00Z"), new BigDecimal("150.50"))));

        AgregadoVentasResponse respuesta = servicio.agregar(Periodo.DIA, dia(1), dia(3), ADMIN);

        assertThat(respuesta.puntos()).extracting(Punto::inicio).containsExactly(dia(1), dia(2), dia(3));
        assertThat(respuesta.puntos()).extracting(Punto::total)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("150.50"), BigDecimal.ZERO);
        assertThat(respuesta.total()).isEqualByComparingTo("150.50");
        assertThat(respuesta.zonaHoraria()).isEqualTo("America/Mexico_City");
        assertThat(respuesta.periodo()).isEqualTo(Periodo.DIA);
    }

    @Test
    void porMesLosPuntosEmpiezanElDiaUnoAunqueElRangoEmpieceDespues() {
        when(ventas.totalesPorPeriodo(any(), any(), anyString(), anyString())).thenReturn(List.of(
                new TotalPeriodo(Instant.parse("2026-01-01T06:00:00Z"), new BigDecimal("10.00")),
                new TotalPeriodo(Instant.parse("2026-03-01T06:00:00Z"), new BigDecimal("20.25"))));

        AgregadoVentasResponse respuesta = servicio.agregar(Periodo.MES,
                LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10), ADMIN);

        assertThat(respuesta.puntos()).extracting(Punto::inicio).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        assertThat(respuesta.total()).isEqualByComparingTo("30.25");
        verify(ventas).totalesPorPeriodo(Instant.parse("2026-01-15T06:00:00Z"),
                Instant.parse("2026-03-11T06:00:00Z"), "month", "America/Mexico_City");
    }

    @Test
    void porAnioUnPuntoPorAnio() {
        when(ventas.totalesPorPeriodo(any(), any(), anyString(), anyString())).thenReturn(List.of());

        AgregadoVentasResponse respuesta = servicio.agregar(Periodo.ANIO,
                LocalDate.of(2024, 6, 1), LocalDate.of(2026, 2, 1), ADMIN);

        assertThat(respuesta.puntos()).extracting(Punto::inicio).containsExactly(
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        assertThat(respuesta.total()).isEqualByComparingTo("0");
    }

    private static LocalDate dia(int dia) {
        return LocalDate.of(2026, 11, dia);
    }
}
