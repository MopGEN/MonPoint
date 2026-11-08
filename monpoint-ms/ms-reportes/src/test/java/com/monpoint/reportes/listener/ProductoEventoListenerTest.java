package com.monpoint.reportes.listener;

import com.monpoint.reportes.model.EventoProcesado;
import com.monpoint.reportes.model.ProductoProyeccion;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import com.monpoint.reportes.repository.ProductoProyeccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoEventoListenerTest {

    private static final Instant GUARDADA = Instant.parse("2026-11-01T12:00:00Z");

    @Mock ProductoProyeccionRepository productos;
    @Mock EventoProcesadoRepository eventos;
    private ProductoEventoListener listener;

    @BeforeEach
    void preparar() {
        listener = new ProductoEventoListener(JsonMapper.builder().build(), productos, eventos,
                Clock.fixed(Instant.parse("2026-11-08T18:00:00Z"), ZoneOffset.UTC));
    }

    @ParameterizedTest
    @CsvSource({"ProductoCreado,true", "ProductoActualizado,true", "ProductoDadoDeBaja,false"})
    void productoNuevoSeProyecta(String tipo, boolean activo) {
        when(productos.findById("p1")).thenReturn(Optional.empty());

        listener.alRecibir(json(producto(tipo, activo, "\"2026-11-02T00:00:00Z\"")));

        var captor = ArgumentCaptor.forClass(ProductoProyeccion.class);
        verify(productos).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("p1");
        assertThat(captor.getValue().getNombre()).isEqualTo("Café");
        assertThat(captor.getValue().getPrecio()).isEqualByComparingTo("25.25");
        assertThat(captor.getValue().isActivo()).isEqualTo(activo);
        assertThat(captor.getValue().getFechaActualizacion()).isEqualTo(Instant.parse("2026-11-02T00:00:00Z"));
        verificarRegistro(tipo);
    }

    @Test
    void eventoMasNuevoActualiza() {
        var guardado = ProductoProyeccion.crear("p1", "Viejo", BigDecimal.ONE, true, GUARDADA);
        when(productos.findById("p1")).thenReturn(Optional.of(guardado));

        listener.alRecibir(json(producto("ProductoActualizado", true, "\"2026-11-02T00:00:00Z\"")));

        verify(productos).save(guardado);
        assertThat(guardado.getNombre()).isEqualTo("Café");
    }

    @Test
    void eventoAntiguoNoPisaLoGuardadoPeroQuedaRegistrado() {
        var guardado = ProductoProyeccion.crear("p1", "Actual", BigDecimal.TEN, true, GUARDADA);
        when(productos.findById("p1")).thenReturn(Optional.of(guardado));

        listener.alRecibir(json(producto("ProductoActualizado", false, "\"2026-10-31T00:00:00Z\"")));

        verify(productos, never()).save(any());
        assertThat(guardado.getNombre()).isEqualTo("Actual");
        verificarRegistro("ProductoActualizado");
    }

    @Test
    void fechaAusenteNoDemuestraAntiguedadYSeAplica() {
        var guardado = ProductoProyeccion.crear("p1", "Actual", BigDecimal.TEN, true, GUARDADA);
        when(productos.findById("p1")).thenReturn(Optional.of(guardado));

        listener.alRecibir(json(producto("ProductoActualizado", true, "null")));

        verify(productos).save(guardado);
        assertThat(guardado.getFechaActualizacion()).isNull();
    }

    @Test
    void duplicadoSeIgnora() {
        when(eventos.existsByEventoId("e1")).thenReturn(true);

        listener.alRecibir(json("{\"eventoId\":\"e1\"}"));

        verifyNoInteractions(productos);
        verify(eventos, never()).save(any());
    }

    @Test
    void sinPrecioFallaSinGuardar() {
        String sinPrecio = producto("ProductoCreado", true, "null").replace("\"precio\":25.25,", "");

        assertThatThrownBy(() -> listener.alRecibir(json(sinPrecio))).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(productos);
        verify(eventos, never()).save(any());
    }

    private void verificarRegistro(String tipo) {
        var captor = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(eventos).save(captor.capture());
        assertThat(captor.getValue().getEventoId()).isEqualTo("e1");
        assertThat(captor.getValue().getTipo()).isEqualTo(tipo);
    }

    private static String producto(String tipo, boolean activo, String fecha) {
        return """
                {"eventoId":"e1","tipo":"%s","version":"1","fecha":"2026-11-02T00:00:01Z","origen":"ms-inventario",
                 "datos":{"productoId":"p1","nombre":"Café","precio":25.25,"activo":%s,"fechaActualizacion":%s}}
                """.formatted(tipo, activo, fecha);
    }

    private static Message json(String cuerpo) {
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
