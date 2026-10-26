package com.monpoint.ventas.service;

import com.mongodb.MongoException;
import com.monpoint.ventas.dto.request.VentaRequest;
import com.monpoint.ventas.event.VentaEventPublisher;
import com.monpoint.ventas.exception.*;
import com.monpoint.ventas.model.*;
import com.monpoint.ventas.repository.*;
import com.monpoint.ventas.security.UsuarioActual;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {
    private static final ValidatorFactory VALIDACION = Validation.buildDefaultValidatorFactory();
    private static final UsuarioActual VENDEDOR = new UsuarioActual("usuario", "VENDEDOR");
    @Mock ProductoReplicaRepository productos;
    @Mock ClienteReplicaRepository clientes;
    @Mock VentaRepository ventas;
    @Mock FolioService folios;
    @Mock VentaEventPublisher publicador;
    @Mock PlatformTransactionManager transacciones;
    private VentaService servicio;

    @BeforeEach void preparar() {
        servicio = new VentaService(productos, clientes, ventas, folios, publicador,
                VALIDACION.getValidator(), transacciones);
    }
    @AfterAll static void cerrarValidacion() { VALIDACION.close(); }

    @ParameterizedTest @ValueSource(strings = {"LECTOR", "OTRO", "admin"})
    void rolNoAutorizadoNoLeeNiEscribe(String rol) {
        assertThatThrownBy(() -> servicio.registrar(solicitud(null), new UsuarioActual("u", rol)))
                .isInstanceOf(AccesoDenegadoException.class);
        verifyNoInteractions(productos, clientes, ventas, folios, publicador, transacciones);
    }

    @Test void identidadVaciaEsNoAutenticada() {
        assertThatThrownBy(() -> servicio.registrar(solicitud(null), new UsuarioActual(" ", "ADMIN")))
                .isInstanceOf(NoAutenticadoException.class);
        verifyNoInteractions(ventas, folios, publicador);
    }

    static Stream<VentaRequest> invalidas() {
        return Stream.of(null, new VentaRequest(null, null), new VentaRequest(null, List.of()),
                new VentaRequest(null, Arrays.asList((VentaRequest.LineaRequest) null)),
                new VentaRequest(null, List.of(new VentaRequest.LineaRequest(" ", 1))),
                new VentaRequest(null, List.of(new VentaRequest.LineaRequest("p", null))),
                new VentaRequest(null, List.of(new VentaRequest.LineaRequest("p", 0))),
                new VentaRequest(null, List.of(new VentaRequest.LineaRequest("p", -1))));
    }

    @ParameterizedTest @MethodSource("invalidas")
    void rechazaEntradaInvalidaSinConsumirFolio(VentaRequest solicitud) {
        assertThatThrownBy(() -> servicio.registrar(solicitud, VENDEDOR)).isInstanceOf(VentaInvalidaException.class);
        verifyNoInteractions(productos, clientes, ventas, folios, publicador, transacciones);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void productoAusenteOInactivoNoGeneraVenta(boolean ausente) {
        when(productos.findById("p")).thenReturn(ausente ? Optional.empty()
                : Optional.of(ProductoReplica.crear("p", "P", BigDecimal.TEN, false, Instant.now())));
        assertThatThrownBy(() -> servicio.registrar(solicitud(null), VENDEDOR))
                .isInstanceOf(VentaInvalidaException.class);
        verifyNoInteractions(ventas, folios, publicador);
    }

    @Test void clienteNoDisponibleConservaMensajeAcordado() {
        productoValido();
        when(clientes.findById("c")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(solicitud("c"), VENDEDOR))
                .isInstanceOf(VentaInvalidaException.class).hasMessage(VentaService.CLIENTE_NO_DISPONIBLE);
        verifyNoInteractions(ventas, folios, publicador);
    }

    @Test void congelaYCalculaAntesDePublicar() {
        var producto = productoValido();
        when(folios.siguiente()).thenReturn(42L);
        servicio.registrar(solicitud(null), VENDEDOR);
        var captor = org.mockito.ArgumentCaptor.forClass(Venta.class);
        var orden = inOrder(ventas, publicador);
        orden.verify(ventas).insert(captor.capture());
        orden.verify(publicador).publicarVentaCreada(isNull(), eq(42L), isNull(), isNull(),
                eq(new BigDecimal("123.45")), any(), anyList());
        var venta = captor.getValue();
        producto.actualizarDesde("Otro", BigDecimal.ONE, false, Instant.now());
        assertThat(venta.getLineas().getFirst().nombreProducto()).isEqualTo("Tinta");
        assertThat(venta.getLineas().getFirst().precioUnitario()).isEqualByComparingTo("41.15");
        assertThat(venta.getTotal()).isEqualByComparingTo("123.45");
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.PENDIENTE);
        assertThatThrownBy(() -> venta.getLineas().clear()).isInstanceOf(UnsupportedOperationException.class);
        verifyNoInteractions(clientes);
    }

    @Test void rechazaDesbordamientoDeProductosRepetidos() {
        productoValido();
        var solicitud = new VentaRequest(null, List.of(new VentaRequest.LineaRequest("p", Integer.MAX_VALUE),
                new VentaRequest.LineaRequest("p", 1)));
        assertThatThrownBy(() -> servicio.registrar(solicitud, VENDEDOR)).isInstanceOf(VentaInvalidaException.class);
        verifyNoInteractions(ventas, folios, publicador);
    }

    @Test void reintentaTransaccionAbortadaPeroPublicaUnaVez() {
        productoValido();
        when(folios.siguiente()).thenReturn(42L);
        var causa = new MongoException(112, "WriteConflict");
        causa.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL);
        when(ventas.insert(any(Venta.class))).thenThrow(new DataAccessResourceFailureException("conflicto", causa))
                .thenAnswer(inv -> inv.getArgument(0));
        servicio.registrar(solicitud(null), VENDEDOR);
        verify(ventas, times(2)).insert(any(Venta.class));
        verify(transacciones).rollback(any());
        verify(transacciones).commit(any());
        verify(publicador).publicarVentaCreada(any(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test void commitDeResultadoDesconocidoNoVuelveACrearLaVenta() {
        productoValido();
        when(folios.siguiente()).thenReturn(42L);
        var causa = new MongoException(91, "Resultado del commit desconocido");
        causa.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL);
        doThrow(new DataAccessResourceFailureException("commit", causa)).when(transacciones).commit(any());
        assertThatThrownBy(() -> servicio.registrar(solicitud(null), VENDEDOR))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(ventas).insert(any(Venta.class));
    }

    private ProductoReplica productoValido() {
        var producto = ProductoReplica.crear("p", "Tinta", new BigDecimal("41.15"), true, Instant.now());
        when(productos.findById("p")).thenReturn(Optional.of(producto));
        return producto;
    }
    private VentaRequest solicitud(String cliente) {
        return new VentaRequest(cliente, List.of(new VentaRequest.LineaRequest("p", 3)));
    }
}
