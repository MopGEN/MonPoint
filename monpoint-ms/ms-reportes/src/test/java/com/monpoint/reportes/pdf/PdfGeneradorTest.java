package com.monpoint.reportes.pdf;

import com.monpoint.reportes.model.Configuracion;
import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.LineaVenta;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.model.VentaProyeccion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfGeneradorTest {

    private static final Instant GENERADO = Instant.parse("2026-11-08T18:00:00Z");
    private static final Configuracion EMPRESA = Configuracion.nueva("Papelería Monpoint", "MON850101AB1",
            "Av. Juárez 100, Guadalajara", "3312345678", GENERADO);

    private final PdfGenerador generador = new PdfGenerador(ZoneId.of("America/Mexico_City"));

    // ------------------------------------------------------------------ ticket (H-051)

    @Test
    void ticket_EsPdfValidoConEncabezadoFolioFechaClienteLineasYTotal() {
        byte[] pdf = generador.ticket(venta(42, "Ana López", "2026-11-08T05:30:00Z", "70.50"), EMPRESA, GENERADO);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(TextoPdf.titulo(pdf)).isEqualTo("Comprobante de venta");
        String texto = TextoPdf.de(pdf);
        assertThat(texto).contains("Papelería Monpoint", "Av. Juárez 100, Guadalajara",
                "RFC: MON850101AB1 · Tel: 3312345678", "Comprobante de venta",
                "Generado el 08/11/2026 12:00 (America/Mexico_City)");
        assertThat(texto).contains("Folio: 42", "Cliente: Ana López");
        // 05:30 UTC del día 8 son las 23:30 del día 7 en Ciudad de México.
        assertThat(texto).contains("Fecha: 07/11/2026 23:30");
        assertThat(texto).contains("Café de olla", "$25.25", "$50.50", "Pan dulce", "$20.00");
        assertThat(texto).contains("Total: $70.50");
    }

    @Test
    void ticket_SinConfiguracionNiCliente_LoIndicaEnLugarDeInventar() {
        String texto = TextoPdf.de(generador.ticket(venta(7, null, "2026-11-08T18:00:00Z", "70.50"), null, GENERADO));

        assertThat(texto).contains("Empresa sin configurar", "Cliente: Público en general");
        assertThat(texto).doesNotContain("RFC:");
    }

    @Test
    void ticket_ImprimeElTotalGuardadoSinRedondearlo() {
        String texto = TextoPdf.de(generador.ticket(venta(1, null, "2026-11-08T18:00:00Z", "1150.155"),
                EMPRESA, GENERADO));

        assertThat(texto).contains("Total: $1,150.155");
    }

    @Test
    void ticket_CaracterFueraDeWinAnsiNoRompeElDocumento() {
        VentaProyeccion venta = VentaProyeccion.de("v1", 3, "Niño ¿Sí?", EstadoVenta.CONFIRMADA,
                List.of(LineaVenta.de("p1", "Café ☕ grande", new BigDecimal("10.00"), 1)),
                new BigDecimal("10.00"), Instant.parse("2026-11-08T18:00:00Z"));

        String texto = TextoPdf.de(generador.ticket(venta, EMPRESA, GENERADO));

        assertThat(texto).contains("Cliente: Niño ¿Sí?", "Café", "grande", "Total: $10.00");
    }

    @ParameterizedTest
    @CsvSource({"0, $0.00", "5, $5.00", "1234.5, '$1,234.50'", "1150.15, '$1,150.15'", "0.125, $0.125",
            "1E+3, '$1,000.00'", "999999.99, '$999,999.99'"})
    void moneda_DosDecimalesMinimoYNuncaRedondea(String valor, String esperado) {
        assertThat(PdfGenerador.moneda(new BigDecimal(valor))).isEqualTo(esperado);
    }

    // ------------------------------------------------------------------ historial (H-052)

    @Test
    void historial_SinVentas_GeneraDocumentoQueLoIndica() {
        byte[] pdf = generador.historial(List.of(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8),
                BigDecimal.ZERO, EMPRESA, GENERADO);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(TextoPdf.de(pdf)).contains("Historial de ventas", "Papelería Monpoint",
                "Periodo: 01/11/2026 al 08/11/2026", "No hay ventas confirmadas en el periodo consultado.",
                "Ventas: 0", "Total acumulado: $0.00");
    }

    @Test
    void historial_ConVentas_ListaCadaUnaYElTotalAcumulado() {
        List<VentaProyeccion> ventas = List.of(
                venta(10, "Ana López", "2026-11-02T18:00:00Z", "70.50"),
                venta(11, null, "2026-11-03T18:00:00Z", "100.10"));

        String texto = TextoPdf.de(generador.historial(ventas, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8),
                new BigDecimal("170.60"), EMPRESA, GENERADO));

        assertThat(texto).contains("Folio", "Fecha", "Cliente", "Total");
        assertThat(texto).contains("02/11/2026 12:00", "Ana López", "$70.50");
        assertThat(texto).contains("03/11/2026 12:00", "Público en general", "$100.10");
        assertThat(texto).contains("Ventas: 2", "Total acumulado: $170.60");
    }

    @Test
    void historial_Largo_ContinuaEnOtrasPaginasRepitiendoElEncabezadoDeLaTabla() {
        List<VentaProyeccion> ventas = IntStream.rangeClosed(1, 120)
                .mapToObj(folio -> venta(folio, "Cliente " + folio, "2026-11-02T18:00:00Z", "1.00"))
                .toList();

        List<String> paginas = TextoPdf.paginas(generador.historial(ventas, LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8), new BigDecimal("120.00"), EMPRESA, GENERADO));

        assertThat(paginas).hasSizeGreaterThan(1);
        assertThat(paginas.get(1)).contains("Folio", "Cliente");
        assertThat(paginas.getLast()).contains("Ventas: 120", "Total acumulado: $120.00");
    }

    // ------------------------------------------------------------------ listados (H-053)

    @Test
    void listadoProductos_MuestraNombrePrecioYTotalDeRegistros() {
        List<ProductoProyeccion> productos = List.of(
                ProductoProyeccion.crear("p1", "Cuaderno profesional", new BigDecimal("45.90"), true, null),
                ProductoProyeccion.crear("p2", "Lápiz", new BigDecimal("5"), true, null));

        byte[] pdf = generador.listadoProductos(productos, EMPRESA, GENERADO);

        assertThat(TextoPdf.titulo(pdf)).isEqualTo("Listado de productos");
        assertThat(TextoPdf.de(pdf)).contains("Papelería Monpoint", "Generado el 08/11/2026 12:00",
                "Producto", "Precio", "Cuaderno profesional", "$45.90", "Lápiz", "$5.00", "Total de registros: 2");
    }

    @Test
    void listadoProductos_Vacio_LoIndica() {
        assertThat(TextoPdf.de(generador.listadoProductos(List.of(), EMPRESA, GENERADO)))
                .contains("No hay productos activos.", "Total de registros: 0");
    }

    @Test
    void listadoClientes_MuestraSusDatosYMarcaLosFaltantes() {
        List<ContraparteProyeccion> clientes = List.of(
                ContraparteProyeccion.cliente("c1", "Ana López", "ana@ejemplo.com", "5512345678", "Calle 1", null),
                ContraparteProyeccion.cliente("c2", "Beto Ruiz", null, null, " ", null));

        String texto = TextoPdf.de(generador.listadoClientes(clientes, null, GENERADO));

        assertThat(texto).contains("Empresa sin configurar", "Listado de clientes",
                "Nombre", "Correo", "Teléfono", "Dirección",
                "Ana López", "ana@ejemplo.com", "5512345678", "Calle 1", "Beto Ruiz", "—", "Total de registros: 2");
    }

    @Test
    void listadoClientes_Vacio_LoIndica() {
        assertThat(TextoPdf.de(generador.listadoClientes(List.of(), EMPRESA, GENERADO)))
                .contains("No hay clientes registrados.", "Total de registros: 0");
    }

    private static VentaProyeccion venta(long folio, String cliente, String fecha, String total) {
        return VentaProyeccion.de("v" + folio, folio, cliente, EstadoVenta.CONFIRMADA, List.of(
                        LineaVenta.de("p1", "Café de olla", new BigDecimal("25.25"), 2),
                        LineaVenta.de("p2", "Pan dulce", new BigDecimal("20.00"), 1)),
                new BigDecimal(total), Instant.parse(fecha));
    }
}
