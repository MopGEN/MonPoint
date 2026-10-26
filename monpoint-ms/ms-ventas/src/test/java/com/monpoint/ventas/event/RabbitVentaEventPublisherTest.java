package com.monpoint.ventas.event;

import com.monpoint.ventas.event.VentaEventPublisher.LineaPublicada;
import com.monpoint.ventas.event.impl.RabbitVentaEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas del publicador, con foco en la rama {@code afterCommit}.
 * <p>
 * Esa rama es la que decide si el evento sale tras confirmar la venta o se descarta al revertirla, y es
 * justamente la que ms-inventario y ms-clientes dejaron sin probar: sus pruebas instancian el publicador sin
 * transacción activa, así que siempre caen en el camino directo.
 */
class RabbitVentaEventPublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RabbitVentaEventPublisher publicador =
            new RabbitVentaEventPublisher(rabbitTemplate, JsonMapper.builder().build());

    private static final List<LineaPublicada> LINEAS = List.of(
            new LineaPublicada("68f1a2b3c4d5e6f701234567", "Tinta para sello", 3,
                    new BigDecimal("41.15")));

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ------------------------------------------------------------------
    // La rama afterCommit
    // ------------------------------------------------------------------

    @Test
    void conTransaccionActiva_noEnviaHastaElCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publicarVenta();

        verifyNoInteractions(rabbitTemplate);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void trasElCommit_enviaUnaSolaVez() {
        TransactionSynchronizationManager.initSynchronization();
        publicarVenta();

        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());

        verify(rabbitTemplate, times(1))
                .send(eq(VentasEventos.EXCHANGE), eq(VentasEventos.RK_VENTA_CREADA), any(Message.class));
    }

    @Test
    void siLaTransaccionSeRevierte_noSeEnviaNada() {
        TransactionSynchronizationManager.initSynchronization();
        publicarVenta();

        // Una reversion invoca afterCompletion(STATUS_ROLLED_BACK) y nunca afterCommit.
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void sinTransaccionActiva_enviaDeInmediato() {
        publicarVenta();

        verify(rabbitTemplate).send(eq(VentasEventos.EXCHANGE), eq(VentasEventos.RK_VENTA_CREADA),
                any(Message.class));
    }

    // ------------------------------------------------------------------
    // El contrato que lee ms-inventario
    // ------------------------------------------------------------------

    @Test
    void elSobreYLosDatosRespetanElContrato() {
        publicarVenta();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());
        String json = new String(captor.getValue().getBody(), StandardCharsets.UTF_8);

        // Sobre comun (Doc 02 §9.2)
        assertThat(json).contains("\"tipo\":\"VentaCreada\"")
                .contains("\"version\":\"1\"")
                .contains("\"origen\":\"ms-ventas\"")
                .contains("\"eventoId\":");

        // Lo unico que ms-inventario lee: ventaId y, por linea, productoId y cantidad.
        assertThat(json).contains("\"ventaId\":\"venta-1\"")
                .contains("\"productoId\":\"68f1a2b3c4d5e6f701234567\"")
                .contains("\"cantidad\":3");

        // El enriquecimiento que ms-reportes necesitara (ms-inventario lo ignora).
        assertThat(json).contains("\"folio\":1042")
                .contains("\"clienteNombre\":\"Ana Ruiz\"")
                .contains("\"nombreProducto\":\"Tinta para sello\"");

        // Importes como numero decimal exacto, nunca como texto (RNF-08).
        assertThat(json).contains("\"total\":123.45").doesNotContain("\"total\":\"");
        assertThat(json).contains("\"precioUnitario\":41.15");
        assertThat(json).doesNotContain("\"subtotal\"", "\"nombreCliente\"");
    }

    @Test
    void elMessageIdEsElEventoId_paraCorrelacionarEnLaConsolaDelBroker() {
        publicarVenta();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(anyString(), anyString(), captor.capture());
        String json = new String(captor.getValue().getBody(), StandardCharsets.UTF_8);

        assertThat(json).contains("\"eventoId\":\"" + captor.getValue().getMessageProperties().getMessageId() + "\"");
    }

    // ------------------------------------------------------------------
    // El contrato de fallo
    // ------------------------------------------------------------------

    @Test
    void siElBrokerFalla_noPropagaPorqueLaVentaYaEstaConfirmada() {
        doThrow(new AmqpException("broker caido")).when(rabbitTemplate)
                .send(anyString(), anyString(), any(Message.class));

        assertThatCode(this::publicarVenta).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------

    private void publicarVenta() {
        publicador.publicarVentaCreada("venta-1", 1042L, "68f1a2b3c4d5e6f701234568", "Ana Ruiz",
                new BigDecimal("123.45"), Instant.parse("2026-10-25T21:00:00Z"), LINEAS);
    }
}
