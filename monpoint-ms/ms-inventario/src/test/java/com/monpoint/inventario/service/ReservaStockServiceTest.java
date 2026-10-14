package com.monpoint.inventario.service;

import com.monpoint.inventario.event.InventarioEventPublisher;
import com.monpoint.inventario.event.LineaReservada;
import com.monpoint.inventario.event.VentaCreadaEvento;
import com.monpoint.inventario.model.EventoProcesado;
import com.monpoint.inventario.model.Producto;
import com.monpoint.inventario.repository.EventoProcesadoRepository;
import com.monpoint.inventario.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lógica de la reserva sin base de datos. La atomicidad real (transacción y descuento condicional) se prueba
 * contra MongoDB en {@code ReservaStockIntegracionTest}.
 */
@ExtendWith(MockitoExtension.class)
class ReservaStockServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private EventoProcesadoRepository eventosProcesados;

    @Mock
    private InventarioEventPublisher eventPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ReservaStockService service;

    @BeforeEach
    void preparar() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new ReservaStockService(mongoTemplate, productoRepository, eventosProcesados, eventPublisher,
                transactionManager);
    }

    private static Producto producto(String id, int existencia, int umbral) {
        Producto p = Producto.crear("Producto " + id, null, new BigDecimal("10.00"), existencia, umbral);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private static VentaCreadaEvento venta(VentaCreadaEvento.Linea... lineas) {
        return new VentaCreadaEvento("ev-1", "VentaCreada", "1", "2026-10-13T10:00:00Z", "ms-ventas",
                new VentaCreadaEvento.Datos("v-1", List.of(lineas)));
    }

    private void descuentoDevuelve(Producto... resultados) {
        var stub = when(mongoTemplate.findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Producto.class)));
        stub.thenReturn(resultados[0], java.util.Arrays.copyOfRange(resultados, 1, resultados.length));
    }

    @Test
    void procesar_ConExistenciaSuficiente_PublicaStockReservadoSinStockBajo() {
        descuentoDevuelve(producto("p1", 7, 5));

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 3)));

        verify(eventosProcesados).insert(any(EventoProcesado.class));
        verify(eventPublisher).publicarStockReservado(eq("v-1"), anyList());
        verify(eventPublisher, never()).publicarStockBajo(any());
        verify(eventPublisher, never()).publicarStockRechazado(any(), any(), any());
    }

    @Test
    void procesar_QueDejaLaExistenciaIgualAlUmbral_PublicaStockBajo() {
        descuentoDevuelve(producto("p1", 5, 5));

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 3)));

        verify(eventPublisher).publicarStockBajo(new LineaReservada("p1", "Producto p1", 3, 5, 5));
    }

    @Test
    void procesar_QueDejaLaExistenciaPorEncimaDelUmbral_NoPublicaStockBajo() {
        descuentoDevuelve(producto("p1", 6, 5));

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 3)));

        verify(eventPublisher, never()).publicarStockBajo(any());
    }

    @Test
    void procesar_ConExistenciaInsuficiente_PublicaStockRechazadoYNoStockReservado() {
        descuentoDevuelve(new Producto[]{null});
        when(productoRepository.findById("p1")).thenReturn(Optional.of(producto("p1", 1, 5)));

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 3)));

        verify(eventPublisher).publicarStockRechazado("v-1", "p1", "EXISTENCIA_INSUFICIENTE");
        verify(eventPublisher, never()).publicarStockReservado(any(), anyList());
        // Una vez dentro de la transacción revertida y otra para anotar el rechazo y descartar su reentrega.
        verify(eventosProcesados, times(2)).insert(any(EventoProcesado.class));
    }

    @Test
    void procesar_ConProductoInexistente_RechazaConMotivoProductoNoEncontrado() {
        descuentoDevuelve(new Producto[]{null});
        when(productoRepository.findById("p1")).thenReturn(Optional.empty());

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 1)));

        verify(eventPublisher).publicarStockRechazado("v-1", "p1", "PRODUCTO_NO_ENCONTRADO");
    }

    @Test
    void procesar_ConProductoDadoDeBaja_RechazaConMotivoProductoInactivo() {
        descuentoDevuelve(new Producto[]{null});
        Producto inactivo = producto("p1", 50, 5);
        inactivo.darDeBaja();
        when(productoRepository.findById("p1")).thenReturn(Optional.of(inactivo));

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 1)));

        verify(eventPublisher).publicarStockRechazado("v-1", "p1", "PRODUCTO_INACTIVO");
    }

    @Test
    void procesar_EventoYaProcesado_SeDescartaSinEfectoNiPublicacion() {
        when(eventosProcesados.existsByEventoId("ev-1")).thenReturn(true);

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 3)));

        verifyNoInteractions(mongoTemplate, eventPublisher);
        verify(eventosProcesados, never()).insert(any(EventoProcesado.class));
    }

    @Test
    void procesar_ConElMismoProductoEnDosLineas_DescuentaUnaSolaVezLaSuma() {
        descuentoDevuelve(producto("p1", 5, 1));

        service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 2), new VentaCreadaEvento.Linea("p1", 3)));

        verify(mongoTemplate, times(1)).findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Producto.class));
        verify(eventPublisher).publicarStockReservado(eq("v-1"), anyList());
    }

    @Test
    void procesar_ConVentaSinLineas_LanzaIllegalArgumentException() {
        VentaCreadaEvento vacio = new VentaCreadaEvento("ev-1", "VentaCreada", "1", null, "ms-ventas",
                new VentaCreadaEvento.Datos("v-1", List.of()));

        assertThrows(IllegalArgumentException.class, () -> service.procesarVentaCreada(vacio));
    }

    @Test
    void procesar_ConCantidadCero_LanzaIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.procesarVentaCreada(venta(new VentaCreadaEvento.Linea("p1", 0))));
    }
}
