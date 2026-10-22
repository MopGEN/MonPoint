package com.monpoint.inventario.service;

import com.monpoint.inventario.event.InventarioEventPublisher;
import com.monpoint.inventario.event.VentaCreadaEvento;
import com.monpoint.inventario.model.EventoProcesado;
import com.monpoint.inventario.model.Producto;
import com.monpoint.inventario.repository.EventoProcesadoRepository;
import com.monpoint.inventario.repository.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * H-023 contra MongoDB REAL (docker compose up -d mongodb): transacción multidocumento, todas o ninguna,
 * e idempotencia por eventoId. El publicador se simula; el listener de RabbitMQ no arranca.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class ReservaStockIntegracionTest {

    private static final String MARCA = "zz-prueba-reserva-";

    @Autowired
    private ReservaStockService reservaStockService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private EventoProcesadoRepository eventosProcesados;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private InventarioEventPublisher eventPublisher;

    private final List<String> productosCreados = new ArrayList<>();
    private final List<String> eventosCreados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        productoRepository.deleteAllById(productosCreados);
        mongoTemplate.remove(Query.query(Criteria.where("eventoId").in(eventosCreados)), EventoProcesado.class);
        productosCreados.clear();
        eventosCreados.clear();
    }

    private Producto guardarProducto(String nombre, int existencia, int umbral) {
        Producto guardado = productoRepository.save(
                Producto.crear(MARCA + nombre, null, new BigDecimal("10.00"), existencia, umbral));
        productosCreados.add(guardado.getId());
        return guardado;
    }

    private VentaCreadaEvento venta(String eventoId, VentaCreadaEvento.Linea... lineas) {
        eventosCreados.add(eventoId);
        return new VentaCreadaEvento(eventoId, "VentaCreada", "1", "2026-10-13T10:00:00Z", "ms-ventas",
                new VentaCreadaEvento.Datos("venta-" + eventoId, List.of(lineas)));
    }

    private int existenciaDe(Producto producto) {
        return productoRepository.findById(producto.getId()).orElseThrow().getExistencia();
    }

    @Test
    void reservar_ConExistenciaSuficiente_DescuentaTodasLasLineasYRegistraElEvento() {
        Producto a = guardarProducto("A", 10, 2);
        Producto b = guardarProducto("B", 4, 2);
        String eventoId = UUID.randomUUID().toString();

        reservaStockService.procesarVentaCreada(venta(eventoId,
                new VentaCreadaEvento.Linea(a.getId(), 3), new VentaCreadaEvento.Linea(b.getId(), 1)));

        assertEquals(7, existenciaDe(a));
        assertEquals(3, existenciaDe(b));
        assertTrue(eventosProcesados.existsByEventoId(eventoId));
        verify(eventPublisher).publicarStockReservado(eq("venta-" + eventoId), anyList());
        verify(eventPublisher, never()).publicarStockBajo(any());
    }

    @Test
    void reservar_QueDejaUnaExistenciaEnElUmbral_PublicaStockBajo() {
        Producto a = guardarProducto("A", 8, 5);

        reservaStockService.procesarVentaCreada(venta(UUID.randomUUID().toString(),
                new VentaCreadaEvento.Linea(a.getId(), 3)));

        assertEquals(5, existenciaDe(a));
        verify(eventPublisher, times(1)).publicarStockBajo(any());
    }

    @Test
    void reservar_ConUnaLineaSinExistencia_NoDescuentaNingunaLinea() {
        Producto a = guardarProducto("A", 10, 2);
        Producto b = guardarProducto("B", 1, 2);
        String eventoId = UUID.randomUUID().toString();

        reservaStockService.procesarVentaCreada(venta(eventoId,
                new VentaCreadaEvento.Linea(a.getId(), 2), new VentaCreadaEvento.Linea(b.getId(), 5)));

        assertEquals(10, existenciaDe(a), "todas o ninguna: la línea A se revierte");
        assertEquals(1, existenciaDe(b));
        verify(eventPublisher).publicarStockRechazado("venta-" + eventoId, b.getId(), "EXISTENCIA_INSUFICIENTE");
        verify(eventPublisher, never()).publicarStockReservado(any(), anyList());
        assertTrue(eventosProcesados.existsByEventoId(eventoId), "el rechazo también se anota");
    }

    @Test
    void reservar_ConProductoDadoDeBaja_RechazaSinDescontar() {
        Producto a = guardarProducto("A", 10, 2);
        a.darDeBaja();
        productoRepository.save(a);

        reservaStockService.procesarVentaCreada(venta(UUID.randomUUID().toString(),
                new VentaCreadaEvento.Linea(a.getId(), 1)));

        assertEquals(10, existenciaDe(a));
        verify(eventPublisher).publicarStockRechazado(any(), eq(a.getId()), eq("PRODUCTO_INACTIVO"));
    }

    @Test
    void reservar_EventoReentregado_NoDescuentaDosVeces() {
        Producto a = guardarProducto("A", 10, 2);
        VentaCreadaEvento evento = venta(UUID.randomUUID().toString(), new VentaCreadaEvento.Linea(a.getId(), 3));

        reservaStockService.procesarVentaCreada(evento);
        reservaStockService.procesarVentaCreada(evento);

        assertEquals(7, existenciaDe(a), "la segunda entrega se descarta");
        verify(eventPublisher, times(1)).publicarStockReservado(any(), anyList());
    }

    @Test
    void reservar_RechazoReentregado_NoVuelveAPublicar() {
        Producto a = guardarProducto("A", 1, 2);
        VentaCreadaEvento evento = venta(UUID.randomUUID().toString(), new VentaCreadaEvento.Linea(a.getId(), 5));

        reservaStockService.procesarVentaCreada(evento);
        reservaStockService.procesarVentaCreada(evento);

        verify(eventPublisher, times(1)).publicarStockRechazado(any(), any(), any());
    }
}
