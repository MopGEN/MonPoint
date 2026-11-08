package com.monpoint.reportes.listener;

import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.TipoContraparte;
import com.monpoint.reportes.repository.ContraparteProyeccionRepository;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteEventoListenerTest {

    private static final Instant GUARDADA = Instant.parse("2026-11-01T12:00:00Z");

    @Mock ContraparteProyeccionRepository contrapartes;
    @Mock EventoProcesadoRepository eventos;
    private ClienteEventoListener listener;

    @BeforeEach
    void preparar() {
        listener = new ClienteEventoListener(JsonMapper.builder().build(), contrapartes, eventos,
                Clock.fixed(Instant.parse("2026-11-08T18:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void clienteNuevoSeProyectaComoContraparteCliente() {
        when(contrapartes.findById("c1")).thenReturn(Optional.empty());

        listener.alRecibir(json(cliente("ClienteCreado", "Ana López", "\"2026-11-02T00:00:00Z\"")));

        var captor = ArgumentCaptor.forClass(ContraparteProyeccion.class);
        verify(contrapartes).save(captor.capture());
        ContraparteProyeccion guardado = captor.getValue();
        assertThat(guardado.getId()).isEqualTo("c1");
        assertThat(guardado.getTipo()).isEqualTo(TipoContraparte.CLIENTE);
        assertThat(guardado.getNombre()).isEqualTo("Ana López");
        assertThat(guardado.getCorreo()).isEqualTo("ana@ejemplo.com");
        assertThat(guardado.getTelefono()).isEqualTo("5512345678");
        assertThat(guardado.getDireccion()).isEqualTo("Calle 1");
        verify(eventos).save(any());
    }

    @Test
    void eventoAntiguoNoPisaLoGuardadoPeroQuedaRegistrado() {
        var guardado = ContraparteProyeccion.cliente("c1", "Actual", null, null, null, GUARDADA);
        when(contrapartes.findById("c1")).thenReturn(Optional.of(guardado));

        listener.alRecibir(json(cliente("ClienteActualizado", "Viejo", "\"2026-10-31T00:00:00Z\"")));

        verify(contrapartes, never()).save(any());
        assertThat(guardado.getNombre()).isEqualTo("Actual");
        verify(eventos).save(any());
    }

    @Test
    void eventoMasNuevoActualiza() {
        var guardado = ContraparteProyeccion.cliente("c1", "Viejo", null, null, null, GUARDADA);
        when(contrapartes.findById("c1")).thenReturn(Optional.of(guardado));

        listener.alRecibir(json(cliente("ClienteActualizado", "Ana López", "\"2026-11-02T00:00:00Z\"")));

        verify(contrapartes).save(guardado);
        assertThat(guardado.getNombre()).isEqualTo("Ana López");
        assertThat(guardado.getTipo()).isEqualTo(TipoContraparte.CLIENTE);
    }

    @Test
    void duplicadoSeIgnora() {
        when(eventos.existsByEventoId("e1")).thenReturn(true);

        listener.alRecibir(json("{\"eventoId\":\"e1\"}"));

        verifyNoInteractions(contrapartes);
        verify(eventos, never()).save(any());
    }

    @Test
    void sinNombreFallaSinGuardar() {
        String sinNombre = cliente("ClienteCreado", "x", "null").replace("\"nombre\":\"x\",", "");

        assertThatThrownBy(() -> listener.alRecibir(json(sinNombre))).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(contrapartes);
        verify(eventos, never()).save(any());
    }

    private static String cliente(String tipo, String nombre, String fecha) {
        return """
                {"eventoId":"e1","tipo":"%s","version":"1","fecha":"2026-11-02T00:00:01Z","origen":"ms-clientes",
                 "datos":{"clienteId":"c1","nombre":"%s","correo":"ana@ejemplo.com","telefono":"5512345678",
                  "direccion":"Calle 1","fechaActualizacion":%s}}
                """.formatted(tipo, nombre, fecha);
    }

    private static Message json(String cuerpo) {
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
