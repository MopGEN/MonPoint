package com.monpoint.reportes.service;

import com.monpoint.reportes.dto.response.DocumentoPdf;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.exception.VentaCanceladaException;
import com.monpoint.reportes.exception.VentaNoDisponibleException;
import com.monpoint.reportes.model.Configuracion;
import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.LineaVenta;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.model.TipoContraparte;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.pdf.PdfGenerador;
import com.monpoint.reportes.repository.ConfiguracionRepository;
import com.monpoint.reportes.repository.ContraparteProyeccionRepository;
import com.monpoint.reportes.repository.ProductoProyeccionRepository;
import com.monpoint.reportes.repository.VentaProyeccionRepository;
import com.monpoint.reportes.security.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentoServiceTest {

    /** 03:00 UTC del 9 de noviembre: en Ciudad de México todavía es el 8. */
    private static final Instant AHORA = Instant.parse("2026-11-09T03:00:00Z");
    private static final UsuarioActual ADMIN = new UsuarioActual("a1", Rol.ADMIN);
    private static final UsuarioActual VENDEDOR = new UsuarioActual("v1", Rol.VENDEDOR);
    private static final byte[] PDF = "%PDF-prueba".getBytes();
    private static final Configuracion EMPRESA = Configuracion.nueva("Monpoint", "MON850101AB1", null, null, AHORA);

    @Mock VentaProyeccionRepository ventas;
    @Mock ProductoProyeccionRepository productos;
    @Mock ContraparteProyeccionRepository contrapartes;
    @Mock ConfiguracionRepository configuracion;
    @Mock PdfGenerador pdf;
    private DocumentoService servicio;

    @BeforeEach
    void preparar() {
        servicio = new DocumentoService(ventas, productos, contrapartes, configuracion, pdf,
                Clock.fixed(AHORA, ZoneOffset.UTC), ZoneId.of("America/Mexico_City"));
    }

    // ------------------------------------------------------------------ ticket

    @Test
    void ticket_VentaConfirmada_GeneraElPdfConLaConfiguracion() {
        VentaProyeccion venta = venta("v1", 42, EstadoVenta.CONFIRMADA, "70.50");
        when(ventas.findById("v1")).thenReturn(Optional.of(venta));
        when(configuracion.findById(Configuracion.ID_UNICO)).thenReturn(Optional.of(EMPRESA));
        when(pdf.ticket(venta, EMPRESA, AHORA)).thenReturn(PDF);

        DocumentoPdf documento = servicio.ticket("v1");

        assertThat(documento.nombreArchivo()).isEqualTo("ticket-folio-42.pdf");
        assertThat(documento.contenido()).isSameAs(PDF);
    }

    @Test
    void ticket_SinConfiguracion_GeneraIgualConEncabezadoVacio() {
        VentaProyeccion venta = venta("v1", 42, EstadoVenta.CONFIRMADA, "70.50");
        when(ventas.findById("v1")).thenReturn(Optional.of(venta));
        when(configuracion.findById(Configuracion.ID_UNICO)).thenReturn(Optional.empty());
        when(pdf.ticket(eq(venta), isNull(), eq(AHORA))).thenReturn(PDF);

        assertThat(servicio.ticket("v1").contenido()).isSameAs(PDF);
    }

    @Test
    void ticket_VentaQueNoEstaEnLaProyeccion_EsNoDisponible() {
        when(ventas.findById("v-pendiente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.ticket("v-pendiente"))
                .isInstanceOf(VentaNoDisponibleException.class)
                .hasMessageContaining("todavía no está disponible");
        verifyNoInteractions(pdf);
    }

    @Test
    void ticket_VentaCancelada_EsConflicto() {
        when(ventas.findById("v1")).thenReturn(Optional.of(venta("v1", 9, EstadoVenta.CANCELADA, "70.50")));

        assertThatThrownBy(() -> servicio.ticket("v1"))
                .isInstanceOf(VentaCanceladaException.class)
                .hasMessageContaining("folio 9");
        verifyNoInteractions(pdf);
    }

    // ------------------------------------------------------------------ historial

    @Test
    void historial_ComoVendedor_EsDenegado() {
        assertThatThrownBy(() -> servicio.historial(dia(1), dia(8), VENDEDOR))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(ventas, pdf);
    }

    @Test
    void historial_RangoInvertido_EsInvalido() {
        assertThatThrownBy(() -> servicio.historial(dia(8), dia(1), ADMIN))
                .isInstanceOf(RangoInvalidoException.class);
        verifyNoInteractions(ventas, pdf);
    }

    @Test
    void historial_MasDeUnAnio_EsInvalido() {
        LocalDate desde = LocalDate.of(2025, 1, 1);

        assertThatThrownBy(() -> servicio.historial(desde, desde.plusDays(DocumentoService.MAX_DIAS_HISTORIAL), ADMIN))
                .isInstanceOf(RangoInvalidoException.class)
                .hasMessageContaining("366");
        verifyNoInteractions(ventas, pdf);
    }

    @Test
    void historial_ElTopeExactoSiSePermite() {
        LocalDate desde = LocalDate.of(2024, 1, 1);
        LocalDate hasta = desde.plusDays(DocumentoService.MAX_DIAS_HISTORIAL - 1);
        when(ventas.confirmadasEntre(any(), any())).thenReturn(List.of());
        when(pdf.historial(any(), any(), any(), any(), any(), any())).thenReturn(PDF);

        assertThat(servicio.historial(desde, hasta, ADMIN).contenido()).isSameAs(PDF);
    }

    @Test
    void historial_ConsultaDiasCompletosEnLaZonaYSumaElTotal() {
        List<VentaProyeccion> confirmadas = List.of(venta("v1", 1, EstadoVenta.CONFIRMADA, "70.50"),
                venta("v2", 2, EstadoVenta.CONFIRMADA, "0.01"));
        when(ventas.confirmadasEntre(Instant.parse("2026-11-01T06:00:00Z"), Instant.parse("2026-11-09T06:00:00Z")))
                .thenReturn(confirmadas);
        when(pdf.historial(any(), any(), any(), any(), any(), any())).thenReturn(PDF);

        DocumentoPdf documento = servicio.historial(dia(1), dia(8), ADMIN);

        ArgumentCaptor<BigDecimal> total = ArgumentCaptor.forClass(BigDecimal.class);
        verify(pdf).historial(eq(confirmadas), eq(dia(1)), eq(dia(8)), total.capture(), isNull(), eq(AHORA));
        assertThat(total.getValue()).isEqualByComparingTo("70.51");
        assertThat(documento.nombreArchivo()).isEqualTo("historial-ventas-2026-11-01-a-2026-11-08.pdf");
    }

    // ------------------------------------------------------------------ listados

    @Test
    void listadoProductos_ComoVendedor_EsDenegado() {
        assertThatThrownBy(() -> servicio.listadoProductos(VENDEDOR)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(productos, pdf);
    }

    @Test
    void listadoProductos_SoloActivosEnOrdenAlfabeticoEspanol() {
        when(productos.findByActivoTrue()).thenReturn(List.of(producto("Zanahoria"), producto("azúcar"),
                producto("Ábaco"), producto("Barra")));
        when(pdf.listadoProductos(any(), any(), any())).thenReturn(PDF);

        DocumentoPdf documento = servicio.listadoProductos(ADMIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProductoProyeccion>> lista = ArgumentCaptor.forClass(List.class);
        verify(pdf).listadoProductos(lista.capture(), isNull(), eq(AHORA));
        assertThat(lista.getValue()).extracting(ProductoProyeccion::getNombre)
                .containsExactly("Ábaco", "azúcar", "Barra", "Zanahoria");
        // La fecha del nombre es la local: a las 03:00 UTC del 9 en Ciudad de México sigue siendo el 8.
        assertThat(documento.nombreArchivo()).isEqualTo("listado-productos-2026-11-08.pdf");
    }

    @Test
    void listadoClientes_ComoVendedor_EsDenegado() {
        assertThatThrownBy(() -> servicio.listadoClientes(VENDEDOR)).isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(contrapartes, pdf);
    }

    @Test
    void listadoClientes_SoloContrapartesClienteEnOrdenAlfabetico() {
        when(contrapartes.findByTipo(TipoContraparte.CLIENTE)).thenReturn(List.of(cliente("Óscar"), cliente("Ana")));
        when(pdf.listadoClientes(any(), any(), any())).thenReturn(PDF);

        DocumentoPdf documento = servicio.listadoClientes(ADMIN);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ContraparteProyeccion>> lista = ArgumentCaptor.forClass(List.class);
        verify(pdf).listadoClientes(lista.capture(), isNull(), eq(AHORA));
        assertThat(lista.getValue()).extracting(ContraparteProyeccion::getNombre).containsExactly("Ana", "Óscar");
        assertThat(documento.nombreArchivo()).isEqualTo("listado-clientes-2026-11-08.pdf");
    }

    private static VentaProyeccion venta(String id, long folio, EstadoVenta estado, String total) {
        return VentaProyeccion.de(id, folio, null, estado,
                List.of(LineaVenta.de("p1", "Café", new BigDecimal(total), 1)), new BigDecimal(total),
                Instant.parse("2026-11-02T18:00:00Z"));
    }

    private static ProductoProyeccion producto(String nombre) {
        return ProductoProyeccion.crear("id-" + nombre, nombre, BigDecimal.ONE, true, null);
    }

    private static ContraparteProyeccion cliente(String nombre) {
        return ContraparteProyeccion.cliente("id-" + nombre, nombre, null, null, null, null);
    }

    private static LocalDate dia(int dia) {
        return LocalDate.of(2026, 11, dia);
    }
}
