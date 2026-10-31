package com.monpoint.ventas.event;

import com.monpoint.ventas.event.VentaEventPublisher.LineaPublicada;
import com.monpoint.ventas.event.VentaEventPublisher.VentaPublicada;
import com.monpoint.ventas.event.impl.RabbitVentaEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Eventos de cierre de la saga (actividad 4.4).
 * <p>
 * El contenido se verifica contra lo que {@code ventas_proy} necesita (Doc 04 §8.2): ms-reportes construye su
 * proyección <b>solo</b> con este evento, sin poder consultar a ms-ventas para completar lo que falte.
 */
class VentaCierreEventosTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitVentaEventPublisher publicador =
            new RabbitVentaEventPublisher(rabbitTemplate, JsonMapper.builder().build());

    private static final VentaPublicada VENTA = new VentaPublicada(
            "68f1a2b3c4d5e6f700000001", 1042L, "68f1a2b3c4d5e6f700000002", "Ana Ruiz",
            new BigDecimal("123.45"),
            Instant.parse("2026-10-25T21:00:00Z"), Instant.parse("2026-10-25T21:00:04Z"),
            List.of(new LineaPublicada("68f1a2b3c4d5e6f700000003", "Tinta para sello", 3,
                    new BigDecimal("41.15"))));

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ------------------------------------------------------------------
    // Contenido
    // ------------------------------------------------------------------

    @Test
    void laConfirmacionLlevaTodoLoQueNecesitaLaProyeccion() {
        publicador.publicarVentaConfirmada(VENTA);

        String json = cuerpoEnviado(VentasEventos.RK_VENTA_CONFIRMADA);
        assertThat(json).contains("\"tipo\":\"VentaConfirmada\"")
                .contains("\"version\":\"1\"")
                .contains("\"origen\":\"ms-ventas\"");
        // Campos de ventas_proy (Doc 04 §8.2)
        assertThat(json).contains("\"ventaId\":\"68f1a2b3c4d5e6f700000001\"")
                .contains("\"folio\":1042")
                .contains("\"clienteNombre\":\"Ana Ruiz\"")
                .contains("\"fechaResolucion\":\"2026-10-25T21:00:04Z\"");
        // Lineas con los valores congelados, no con el catalogo actual
        assertThat(json).contains("\"nombreProducto\":\"Tinta para sello\"")
                .contains("\"precioUnitario\":41.15")
                .contains("\"cantidad\":3");
        // Importes como numero decimal exacto, nunca como texto (RNF-08)
        assertThat(json).contains("\"total\":123.45").doesNotContain("\"total\":\"");
    }

    @Test
    void laConfirmacionNoLlevaMotivo() {
        publicador.publicarVentaConfirmada(VENTA);

        // Ni siquiera como null: el tipo del evento y la presencia del motivo dirian lo mismo,
        // y dos formas de preguntar lo mismo acaban contradiciendose.
        assertThat(cuerpoEnviado(VentasEventos.RK_VENTA_CONFIRMADA)).doesNotContain("motivo");
    }

    @Test
    void laCancelacionLlevaElCodigoDeInventarioYElRestoDeLaVenta() {
        publicador.publicarVentaCancelada(VENTA, VentasEventos.MOTIVO_EXISTENCIA_INSUFICIENTE);

        String json = cuerpoEnviado(VentasEventos.RK_VENTA_CANCELADA);
        assertThat(json).contains("\"tipo\":\"VentaCancelada\"")
                .contains("\"motivo\":\"EXISTENCIA_INSUFICIENTE\"")
                // El evento lleva el codigo; la frase para el cajero vive en la venta, no aqui.
                .doesNotContain("existencia insuficiente de");
        assertThat(json).contains("\"folio\":1042")
                .contains("\"total\":123.45")
                .contains("\"nombreProducto\":\"Tinta para sello\"");
    }

    @Test
    void unaVentaSinClienteViajaConLosCamposEnNulo() {
        VentaPublicada sinCliente = new VentaPublicada(VENTA.ventaId(), VENTA.folio(), null, null,
                VENTA.total(), VENTA.fechaCreacion(), VENTA.fechaResolucion(), VENTA.lineas());

        publicador.publicarVentaConfirmada(sinCliente);

        // El esquema admite clienteId y clienteNombre nulos (Doc 04 §6.1): el campo viaja, vacio.
        assertThat(cuerpoEnviado(VentasEventos.RK_VENTA_CONFIRMADA))
                .contains("\"clienteId\":null").contains("\"clienteNombre\":null");
    }

    // ------------------------------------------------------------------
    // Siguen la misma regla de transaccion que VentaCreada
    // ------------------------------------------------------------------

    @Test
    void conTransaccionActiva_losDosEsperanAlCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publicador.publicarVentaConfirmada(VENTA);
        publicador.publicarVentaCancelada(VENTA, VentasEventos.MOTIVO_PRODUCTO_INACTIVO);

        verifyNoInteractions(rabbitTemplate);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
    }

    @Test
    void siLaResolucionSeRevierte_noSeAnunciaNada() {
        TransactionSynchronizationManager.initSynchronization();
        publicador.publicarVentaConfirmada(VENTA);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        // Si la venta no quedo CONFIRMADA en la base, nadie puede enterarse de que lo esta.
        verifyNoInteractions(rabbitTemplate);
    }

    // ------------------------------------------------------------------

    private String cuerpoEnviado(String routingKey) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(VentasEventos.EXCHANGE), eq(routingKey), captor.capture());
        return new String(captor.getValue().getBody(), StandardCharsets.UTF_8);
    }
}
