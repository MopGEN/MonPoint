package com.monpoint.ventas.service;

import com.monpoint.ventas.event.VentaEventPublisher;
import com.monpoint.ventas.exception.AccesoDenegadoException;
import com.monpoint.ventas.exception.NoAutenticadoException;
import com.monpoint.ventas.exception.VentaInvalidaException;
import com.monpoint.ventas.exception.VentaNoEncontradaException;
import com.monpoint.ventas.model.EstadoVenta;
import com.monpoint.ventas.model.LineaVenta;
import com.monpoint.ventas.model.Venta;
import com.monpoint.ventas.repository.ClienteReplicaRepository;
import com.monpoint.ventas.repository.ProductoReplicaRepository;
import com.monpoint.ventas.repository.VentaRepository;
import com.monpoint.ventas.security.UsuarioActual;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Reglas de la consulta de una venta (actividad 5.2), sin levantar contexto de Spring. */
class VentaConsultaServiceTest {

    private static final String ID = "68f1a2b3c4d5e6f700000001";
    private static final String CLIENTE = "68f1a2b3c4d5e6f700000002";
    private static final String PRODUCTO = "68f1a2b3c4d5e6f700000003";
    private static final UsuarioActual VENDEDOR = new UsuarioActual("u1", "VENDEDOR");

    private final VentaRepository ventas = mock(VentaRepository.class);
    private final VentaService servicio = new VentaService(
            mock(ProductoReplicaRepository.class), mock(ClienteReplicaRepository.class), ventas,
            mock(FolioService.class), mock(VentaEventPublisher.class), mock(Validator.class),
            mock(PlatformTransactionManager.class));

    /**
     * El id lo asigna MongoDB al insertar, así que una Venta construida en memoria lo tiene en null y no
     * hay método para fijarlo: el modelo lo impide a propósito. Se inyecta por reflexión solo aquí, para
     * poder comprobar que el mapeo lo traslada.
     */
    private static Venta ventaDeTres() {
        Venta venta = Venta.pendiente(CLIENTE, "Ana Ruiz",
                List.of(new LineaVenta(PRODUCTO, "Tinta para sello", new BigDecimal("41.15"), 3)),
                Instant.parse("2026-10-31T21:00:00Z"));
        venta.asignarFolio(1042L);
        ReflectionTestUtils.setField(venta, "id", ID);
        return venta;
    }

    @Test
    void mapeaElDetalleYCalculaElSubtotalDeCadaLinea() {
        when(ventas.findById(ID)).thenReturn(Optional.of(ventaDeTres()));

        var detalle = servicio.consultar(ID, VENDEDOR);

        assertThat(detalle.id()).isEqualTo(ID);
        assertThat(detalle.folio()).isEqualTo(1042L);
        assertThat(detalle.estado()).isEqualTo(EstadoVenta.PENDIENTE);
        assertThat(detalle.clienteNombre()).isEqualTo("Ana Ruiz");
        assertThat(detalle.lineas()).singleElement().satisfies(linea -> {
            assertThat(linea.nombreProducto()).isEqualTo("Tinta para sello");
            assertThat(linea.cantidad()).isEqualTo(3);
            // 41.15 × 3. El cliente web no debe recalcularlo: en JavaScript daría 123.44999999999999.
            assertThat(linea.subtotal()).isEqualByComparingTo("123.45");
        });
        assertThat(detalle.total()).isEqualByComparingTo("123.45");
    }

    @Test
    void unaVentaPendienteNoTraeMotivoNiFechaDeResolucion() {
        when(ventas.findById(ID)).thenReturn(Optional.of(ventaDeTres()));

        var detalle = servicio.consultar(ID, VENDEDOR);

        assertThat(detalle.motivo()).isNull();
        assertThat(detalle.fechaResolucion()).isNull();
    }

    @Test
    void unaVentaCanceladaTraeElMotivoYaRedactado() {
        Venta venta = ventaDeTres();
        venta.cancelar("existencia insuficiente de Tinta para sello, solicitado 3", PRODUCTO,
                Instant.parse("2026-10-31T21:00:04Z"));
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));

        var detalle = servicio.consultar(ID, VENDEDOR);

        assertThat(detalle.estado()).isEqualTo(EstadoVenta.CANCELADA);
        assertThat(detalle.motivo()).isEqualTo("existencia insuficiente de Tinta para sello, solicitado 3");
        // El identificador acompana a la frase: la caja senala la linea sin analizar el texto.
        assertThat(detalle.productoIdRechazado()).isEqualTo(PRODUCTO);
        assertThat(detalle.fechaResolucion()).isEqualTo(Instant.parse("2026-10-31T21:00:04Z"));
    }

    @Test
    void cancelarExigeElProductoQueProvocoElRechazo() {
        Venta venta = ventaDeTres();

        assertThatThrownBy(() -> venta.cancelar("motivo", null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> venta.cancelar("motivo", "   ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        // La venta sigue PENDIENTE: un intento invalido no la resuelve a medias.
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.PENDIENTE);
    }

    @Test
    void unaVentaConfirmadaNoTraeMotivoPeroSiFechaDeResolucion() {
        Venta venta = ventaDeTres();
        venta.confirmar(Instant.parse("2026-10-31T21:00:04Z"));
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));

        var detalle = servicio.consultar(ID, VENDEDOR);

        assertThat(detalle.estado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(detalle.motivo()).isNull();
        assertThat(detalle.productoIdRechazado()).isNull();
        assertThat(detalle.fechaResolucion()).isNotNull();
    }

    @Test
    void unaVentaSinClienteTraeLosDosCamposDeClienteEnNulo() {
        Venta venta = Venta.pendiente(null, null,
                List.of(new LineaVenta(PRODUCTO, "Tinta para sello", new BigDecimal("41.15"), 1)),
                Instant.parse("2026-10-31T21:00:00Z"));
        venta.asignarFolio(1043L);
        ReflectionTestUtils.setField(venta, "id", ID);
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));

        var detalle = servicio.consultar(ID, VENDEDOR);

        assertThat(detalle.clienteId()).isNull();
        assertThat(detalle.clienteNombre()).isNull();
    }

    @Test
    void ventaInexistenteLanzaNoEncontrada() {
        when(ventas.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(ID, VENDEDOR))
                .isInstanceOf(VentaNoEncontradaException.class)
                .hasMessageContaining(ID);
    }

    /**
     * findById con una cadena que no es ObjectId no devuelve vacío: revienta al convertir y produciría un
     * 500. La guarda tiene que actuar antes de tocar la base.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "no-es-un-objectid", "68f1a2b3c4d5e6f70000000", "zzzzzzzzzzzzzzzzzzzzzzzz"})
    void identificadorMalFormadoLanzaVentaInvalidaSinTocarElRepositorio(String id) {
        assertThatThrownBy(() -> servicio.consultar(id, VENDEDOR))
                .isInstanceOf(VentaInvalidaException.class);
        verifyNoInteractions(ventas);
    }

    @Test
    void sinUsuarioLanzaNoAutenticado() {
        assertThatThrownBy(() -> servicio.consultar(ID, null)).isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.consultar(ID, new UsuarioActual("", "VENDEDOR")))
                .isInstanceOf(NoAutenticadoException.class);
        assertThatThrownBy(() -> servicio.consultar(ID, new UsuarioActual("u1", " ")))
                .isInstanceOf(NoAutenticadoException.class);
        verifyNoInteractions(ventas);
    }

    @Test
    void rolAjenoLanzaAccesoDenegadoConLaAccionCorrecta() {
        assertThatThrownBy(() -> servicio.consultar(ID, new UsuarioActual("u1", "INVITADO")))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessage("Solo ADMIN o VENDEDOR pueden consultar ventas");
        verifyNoInteractions(ventas);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "VENDEDOR"})
    void amboRolesPuedenConsultarCualquierVenta(String rol) {
        when(ventas.findById(ID)).thenReturn(Optional.of(ventaDeTres()));

        assertThat(servicio.consultar(ID, new UsuarioActual("u1", rol)).folio()).isEqualTo(1042L);
    }
}
