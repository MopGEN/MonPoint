package com.monpoint.reportes.repository;

import com.monpoint.reportes.dto.response.AgregadoVentasResponse;
import com.monpoint.reportes.dto.response.AgregadoVentasResponse.Punto;
import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.LineaVenta;
import com.monpoint.reportes.model.Periodo;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.AgregacionVentasService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agregación real contra el MongoDB local. Las ventas de prueba son del año 2000 para no mezclarse con datos
 * de desarrollo, y al final se borran solo las que creó esta prueba.
 * <p>
 * En enero de 2000 Ciudad de México estaba en UTC-6, así que la medianoche local son las 06:00 UTC.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AgregacionVentasIntegracionTest {

    private static final UsuarioActual ADMIN = new UsuarioActual("a1", Rol.ADMIN);

    @Autowired VentaProyeccionRepository ventas;
    @Autowired AgregacionVentasService servicio;
    private final List<String> creadas = new ArrayList<>();

    @BeforeEach
    void sembrar() {
        // 14 ene 23:30 local: en UTC ya es el día 15, pero debe contar en el 14.
        venta("2000-01-15T05:30:00Z", "100.10", EstadoVenta.CONFIRMADA);
        // 15 ene 00:00 local exacto: primer instante del día 15.
        venta("2000-01-15T06:00:00Z", "50.05", EstadoVenta.CONFIRMADA);
        // 16 ene 23:59:59 local: último segundo dentro del rango.
        venta("2000-01-17T05:59:59Z", "0.01", EstadoVenta.CONFIRMADA);
        // 17 ene 00:00 local: ya fuera del rango que termina el 16.
        venta("2000-01-17T06:00:00Z", "999.99", EstadoVenta.CONFIRMADA);
        // Cancelada: nunca suma.
        venta("2000-01-15T18:00:00Z", "500.00", EstadoVenta.CANCELADA);
    }

    @AfterEach
    void limpiarSoloLasDeEstaPrueba() {
        ventas.deleteAllById(creadas);
    }

    @Test
    void porDiaRespetaLaZonaHorariaYLosBordesDelRango() {
        AgregadoVentasResponse respuesta = servicio.agregar(Periodo.DIA,
                LocalDate.of(2000, 1, 13), LocalDate.of(2000, 1, 16), ADMIN);

        assertThat(respuesta.puntos()).extracting(Punto::inicio).containsExactly(
                LocalDate.of(2000, 1, 13), LocalDate.of(2000, 1, 14),
                LocalDate.of(2000, 1, 15), LocalDate.of(2000, 1, 16));
        assertThat(respuesta.puntos().get(0).total()).isEqualByComparingTo("0");
        assertThat(respuesta.puntos().get(1).total()).isEqualByComparingTo("100.10");
        assertThat(respuesta.puntos().get(2).total()).isEqualByComparingTo("50.05");
        assertThat(respuesta.puntos().get(3).total()).isEqualByComparingTo("0.01");
        assertThat(respuesta.total()).isEqualByComparingTo("150.16");
    }

    @Test
    void porMesSumaConCentavosExactos() {
        AgregadoVentasResponse respuesta = servicio.agregar(Periodo.MES,
                LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 31), ADMIN);

        assertThat(respuesta.puntos()).hasSize(1);
        assertThat(respuesta.puntos().getFirst().inicio()).isEqualTo(LocalDate.of(2000, 1, 1));
        // 100.10 + 50.05 + 0.01 + 999.99: con double daría 1150.1499999...
        assertThat(respuesta.total()).isEqualByComparingTo("1150.15");
        assertThat(respuesta.total().scale()).isGreaterThanOrEqualTo(2);
    }

    private void venta(String fecha, String total, EstadoVenta estado) {
        String id = new ObjectId().toHexString();
        BigDecimal importe = new BigDecimal(total);
        ventas.save(VentaProyeccion.de(id, 9_000_000L + creadas.size(), null, estado,
                List.of(LineaVenta.de("p-prueba", "Producto de prueba", importe, 1)), importe,
                Instant.parse(fecha)));
        creadas.add(id);
    }
}
