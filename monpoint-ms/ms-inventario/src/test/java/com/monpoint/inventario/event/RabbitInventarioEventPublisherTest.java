package com.monpoint.inventario.event;

import com.monpoint.inventario.event.impl.RabbitInventarioEventPublisher;
import com.monpoint.inventario.model.Producto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitInventarioEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitInventarioEventPublisher publisher;

    @BeforeEach
    void preparar() {
        publisher = new RabbitInventarioEventPublisher(rabbitTemplate, JsonMapper.builder().build());
    }

    private String enviado(String routingKey) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("inventario.eventos"), eq(routingKey), captor.capture());
        return new String(captor.getValue().getBody(), StandardCharsets.UTF_8);
    }

    @Test
    void publicarProductoCreado_IncluyeIdNombrePrecioEstadoYSobreComun() {
        publisher.publicarProductoCreado(Producto.crear("Aceite 1L", null, new BigDecimal("45.50"), 10, null));

        String json = enviado("producto.creado");
        assertTrue(json.contains("\"tipo\":\"ProductoCreado\""), json);
        assertTrue(json.contains("\"origen\":\"ms-inventario\""), json);
        assertTrue(json.contains("\"eventoId\":\""), json);
        assertTrue(json.contains("\"version\":"), json);
        assertTrue(json.contains("\"fecha\":\""), json);
        assertTrue(json.contains("\"nombre\":\"Aceite 1L\""), json);
        assertTrue(json.contains("\"precio\":45.50"), json);
        assertTrue(json.contains("\"activo\":true"), json);
        assertTrue(json.contains("\"fechaActualizacion\""), json);
    }

    @Test
    void publicarProductoActualizadoYDadoDeBaja_UsanSuTipoYRoutingKey() {
        Producto producto = Producto.crear("Aceite", null, BigDecimal.TEN, 1, null);
        publisher.publicarProductoActualizado(producto);
        producto.darDeBaja();
        publisher.publicarProductoDadoDeBaja(producto);

        assertTrue(enviado("producto.actualizado").contains("\"tipo\":\"ProductoActualizado\""));
        String baja = enviado("producto.dado-de-baja");
        assertTrue(baja.contains("\"tipo\":\"ProductoDadoDeBaja\""), baja);
        assertTrue(baja.contains("\"activo\":false"), baja);
    }

    @Test
    void publicarStockReservado_IncluyeLaExistenciaResultanteDeCadaProducto() {
        publisher.publicarStockReservado("venta-1", List.of(
                new LineaReservada("p1", "Aceite", 3, 7, 5),
                new LineaReservada("p2", "Sal", 1, 20, 5)));

        String json = enviado("stock.reservado");
        assertTrue(json.contains("\"tipo\":\"StockReservado\""), json);
        assertTrue(json.contains("\"ventaId\":\"venta-1\""), json);
        assertTrue(json.contains("\"productoId\":\"p1\""), json);
        assertTrue(json.contains("\"existenciaResultante\":7"), json);
        assertTrue(json.contains("\"existenciaResultante\":20"), json);
    }

    @Test
    void publicarStockRechazado_IncluyeProductoYMotivo() {
        publisher.publicarStockRechazado("venta-1", "p9", "EXISTENCIA_INSUFICIENTE");

        String json = enviado("stock.rechazado");
        assertTrue(json.contains("\"tipo\":\"StockRechazado\""), json);
        assertTrue(json.contains("\"productoId\":\"p9\""), json);
        assertTrue(json.contains("\"motivo\":\"EXISTENCIA_INSUFICIENTE\""), json);
    }

    @Test
    void publicarStockBajo_IncluyeIdNombreYExistenciaResultante() {
        publisher.publicarStockBajo(new LineaReservada("p1", "Aceite", 3, 2, 5));

        String json = enviado("stock.bajo");
        assertTrue(json.contains("\"tipo\":\"StockBajo\""), json);
        assertTrue(json.contains("\"productoId\":\"p1\""), json);
        assertTrue(json.contains("\"nombre\":\"Aceite\""), json);
        assertTrue(json.contains("\"existenciaResultante\":2"), json);
    }

    @Test
    void publicar_ConElBrokerCaido_NoPropagaLaExcepcion() {
        doThrow(new RuntimeException("broker caído"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        assertDoesNotThrow(() -> publisher.publicarStockBajo(new LineaReservada("p1", "Aceite", 3, 2, 5)));
    }
}
