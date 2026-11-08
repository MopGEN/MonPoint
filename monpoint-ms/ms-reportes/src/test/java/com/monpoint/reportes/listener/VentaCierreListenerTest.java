package com.monpoint.reportes.listener;

import com.monpoint.reportes.model.EstadoVenta;
import com.monpoint.reportes.model.EventoProcesado;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import com.monpoint.reportes.repository.VentaProyeccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaCierreListenerTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T18:00:00Z");

    @Mock VentaProyeccionRepository ventas;
    @Mock EventoProcesadoRepository eventos;
    private VentaCierreListener listener;

    @BeforeEach
    void preparar() {
        listener = new VentaCierreListener(JsonMapper.builder().build(), ventas, eventos,
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void confirmadaSeProyectaConLineasCongeladasYFechaDeResolucion() {
        listener.alRecibir(json(venta("VentaConfirmada", "\"Ana López\"", null)));

        var captor = ArgumentCaptor.forClass(VentaProyeccion.class);
        verify(ventas).save(captor.capture());
        VentaProyeccion venta = captor.getValue();
        assertThat(venta.getId()).isEqualTo("v1");
        assertThat(venta.getFolio()).isEqualTo(42L);
        assertThat(venta.getClienteNombre()).isEqualTo("Ana López");
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(venta.getTotal()).isEqualByComparingTo("70.50");
        assertThat(venta.getFecha()).isEqualTo(Instant.parse("2026-11-08T05:30:00Z"));
        assertThat(venta.getLineas()).hasSize(2);
        assertThat(venta.getLineas().getFirst().getNombreProducto()).isEqualTo("Café");
        assertThat(venta.getLineas().getFirst().getPrecioUnitario()).isEqualByComparingTo("25.25");
        assertThat(venta.getLineas().getFirst().getCantidad()).isEqualTo(2);
        assertThat(venta.getLineas().getFirst().importe()).isEqualByComparingTo("50.50");

        var registro = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(eventos).save(registro.capture());
        assertThat(registro.getValue().getEventoId()).isEqualTo("e1");
        assertThat(registro.getValue().getTipo()).isEqualTo("VentaConfirmada");
        assertThat(registro.getValue().getFechaProcesado()).isEqualTo(AHORA);
    }

    @Test
    void canceladaTambienSeGuardaParaQueElTicketRespondaConflicto() {
        listener.alRecibir(json(venta("VentaCancelada", "null", "\"Sin existencias de Café\"")));

        var captor = ArgumentCaptor.forClass(VentaProyeccion.class);
        verify(ventas).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoVenta.CANCELADA);
        assertThat(captor.getValue().getClienteNombre()).isNull();
        verify(eventos).save(any());
    }

    @Test
    void duplicadoNoTocaLaProyeccionAunqueVengaIncompleto() {
        when(eventos.existsByEventoId("e1")).thenReturn(true);

        listener.alRecibir(json("{\"eventoId\":\"e1\"}"));

        verifyNoInteractions(ventas);
        verify(eventos, never()).save(any());
    }

    @Test
    void tipoDesconocidoFallaParaTerminarEnFallidos() {
        assertThatThrownBy(() -> listener.alRecibir(json(venta("VentaCreada", "null", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VentaCreada");
        verifyNoInteractions(ventas);
        verify(eventos, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ventaId", "folio", "total", "fechaResolucion", "lineas"})
    void datoObligatorioAusenteFallaSinGuardarNada(String campo) {
        String json = venta("VentaConfirmada", "null", null)
                .replaceFirst("\"" + campo + "\":", "\"ignorado_" + campo + "\":");

        assertThatThrownBy(() -> listener.alRecibir(json(json))).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(ventas);
        verify(eventos, never()).save(any());
    }

    @Test
    void sinEventoIdFalla() {
        assertThatThrownBy(() -> listener.alRecibir(json("{\"tipo\":\"VentaConfirmada\"}")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventoId");
    }

    private static String venta(String tipo, String clienteNombre, String motivo) {
        return """
                {"eventoId":"e1","tipo":"%s","version":"1","fecha":"2026-11-08T05:30:01Z","origen":"ms-ventas",
                 "datos":{"ventaId":"v1","folio":42,"clienteId":null,"clienteNombre":%s,"total":70.50,
                  "fechaCreacion":"2026-11-08T05:29:58Z","fechaResolucion":"2026-11-08T05:30:00Z",
                  "lineas":[{"productoId":"p1","nombreProducto":"Café","cantidad":2,"precioUnitario":25.25},
                            {"productoId":"p2","nombreProducto":"Pan","cantidad":1,"precioUnitario":20.00}],
                  "motivo":%s,"campoNuevo":"se ignora"}}
                """.formatted(tipo, clienteNombre, motivo);
    }

    private static Message json(String cuerpo) {
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
