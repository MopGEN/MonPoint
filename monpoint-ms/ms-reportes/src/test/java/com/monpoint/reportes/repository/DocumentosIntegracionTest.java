package com.monpoint.reportes.repository;

import com.monpoint.reportes.dto.response.DocumentoPdf;
import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.LineaVenta;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.pdf.TextoPdf;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.DocumentoService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
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
 * Consultas de los PDF contra el MongoDB local. Las ventas son del año 2000 y los productos llevan un sufijo
 * único, para no mezclarse con datos de desarrollo; al final se borra solo lo que creó cada prueba.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class DocumentosIntegracionTest {

    private static final UsuarioActual ADMIN = new UsuarioActual("a1", Rol.ADMIN);

    @Autowired VentaProyeccionRepository ventas;
    @Autowired ProductoProyeccionRepository productos;
    @Autowired DocumentoService documentos;
    private final List<String> ventasCreadas = new ArrayList<>();
    private final List<String> productosCreados = new ArrayList<>();
    private final String sufijo = new ObjectId().toHexString();

    @AfterEach
    void limpiarSoloLoDeEstaPrueba() {
        ventas.deleteAllById(ventasCreadas);
        productos.deleteAllById(productosCreados);
    }

    @Test
    void confirmadasEntre_DevuelveSoloConfirmadasDelRangoEnOrdenCronologico() {
        String tarde = venta(901, "2000-01-15T20:00:00Z", EstadoVenta.CONFIRMADA, "20.00");
        String temprano = venta(902, "2000-01-15T07:00:00Z", EstadoVenta.CONFIRMADA, "10.00");
        venta(903, "2000-01-15T12:00:00Z", EstadoVenta.CANCELADA, "999.00");
        venta(904, "2000-01-17T06:00:00Z", EstadoVenta.CONFIRMADA, "999.00");   // 17 ene 00:00 local: fuera

        List<VentaProyeccion> resultado = ventas.confirmadasEntre(Instant.parse("2000-01-15T06:00:00Z"),
                Instant.parse("2000-01-17T06:00:00Z"));

        assertThat(resultado).extracting(VentaProyeccion::getId).containsExactly(temprano, tarde);
        assertThat(resultado.getFirst().getTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void historial_PdfConLasVentasDelPeriodoYSuTotalExacto() {
        venta(911, "2000-01-15T07:00:00Z", EstadoVenta.CONFIRMADA, "100.10");
        venta(912, "2000-01-16T07:00:00Z", EstadoVenta.CONFIRMADA, "50.05");
        venta(913, "2000-01-16T08:00:00Z", EstadoVenta.CANCELADA, "999.99");

        DocumentoPdf documento = documentos.historial(LocalDate.of(2000, 1, 15), LocalDate.of(2000, 1, 16), ADMIN);

        String texto = TextoPdf.de(documento.contenido());
        assertThat(texto).contains("Periodo: 15/01/2000 al 16/01/2000", "911", "912", "$100.10", "$50.05",
                "Ventas: 2", "Total acumulado: $150.15");
        assertThat(texto).doesNotContain("913", "999.99");
    }

    @Test
    void ticket_SeGeneraDesdeLaProyeccionGuardadaConSuTotalExacto() {
        String id = venta(921, "2000-01-15T07:00:00Z", EstadoVenta.CONFIRMADA, "1150.15");

        DocumentoPdf documento = documentos.ticket(id);

        assertThat(documento.nombreArchivo()).isEqualTo("ticket-folio-921.pdf");
        assertThat(TextoPdf.de(documento.contenido())).contains("Folio: 921", "Producto de prueba",
                "Total: $1,150.15");
    }

    @Test
    void findByActivoTrue_ExcluyeLosDadosDeBaja() {
        String activo = producto("Activo " + sufijo, true);
        String inactivo = producto("Inactivo " + sufijo, false);

        List<String> ids = productos.findByActivoTrue().stream().map(ProductoProyeccion::getId).toList();

        assertThat(ids).contains(activo).doesNotContain(inactivo);
    }

    private String venta(long folio, String fecha, EstadoVenta estado, String total) {
        String id = new ObjectId().toHexString();
        BigDecimal importe = new BigDecimal(total);
        ventas.save(VentaProyeccion.de(id, folio, null, estado,
                List.of(LineaVenta.de("p-prueba", "Producto de prueba", importe, 1)), importe, Instant.parse(fecha)));
        ventasCreadas.add(id);
        return id;
    }

    private String producto(String nombre, boolean activo) {
        String id = new ObjectId().toHexString();
        productos.save(ProductoProyeccion.crear(id, nombre, BigDecimal.TEN, activo, null));
        productosCreados.add(id);
        return id;
    }
}
