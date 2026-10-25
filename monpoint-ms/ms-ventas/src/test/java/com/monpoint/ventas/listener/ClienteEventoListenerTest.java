package com.monpoint.ventas.listener;

import com.monpoint.ventas.model.ClienteReplica;
import com.monpoint.ventas.model.EventoProcesado;
import com.monpoint.ventas.repository.ClienteReplicaRepository;
import com.monpoint.ventas.repository.EventoProcesadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClienteEventoListenerTest {
    private static final Instant FECHA = Instant.parse("2026-10-25T12:00:00Z");
    @Mock ClienteReplicaRepository replicas;
    @Mock EventoProcesadoRepository eventos;
    private ClienteEventoListener listener;

    @BeforeEach
    void preparar() {
        listener = new ClienteEventoListener(JsonMapper.builder().build(), replicas, eventos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ClienteCreado", "ClienteActualizado"})
    void creaReplicaYRegistraEvento(String tipo) {
        when(replicas.findById("entidad")).thenReturn(Optional.empty());
        listener.alRecibir(mensaje(tipo, FECHA));
        var captor = ArgumentCaptor.forClass(ClienteReplica.class);
        verify(replicas).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("entidad");
        assertThat(captor.getValue().getNombre()).isEqualTo("Nuevo");
        assertThat(captor.getValue().getFechaActualizacion()).isEqualTo(FECHA);
        verificarRegistro(tipo);
    }

    @Test
    void duplicadoNoTocaReplicaInclusoSiDatosIncompletos() {
        when(eventos.existsByEventoId("evento")).thenReturn(true);
        listener.alRecibir(json("{\"eventoId\":\"evento\"}"));
        verifyNoInteractions(replicas);
        verify(eventos, never()).save(any());
    }

    @Test
    void antiguoConservaReplicaPeroRegistraEvento() {
        var replica = existente(FECHA);
        when(replicas.findById("entidad")).thenReturn(Optional.of(replica));
        listener.alRecibir(mensaje("ClienteActualizado", FECHA.minusSeconds(1)));
        assertThat(replica.getNombre()).isEqualTo("Anterior");
        assertThat(replica.getFechaActualizacion()).isEqualTo(FECHA);
        verify(replicas, never()).save(any());
        verificarRegistro("ClienteActualizado");
    }

    @ParameterizedTest
    @CsvSource(value = {"0,0", "0,1", "0,NULL", "NULL,0", "NULL,NULL"}, nullValues = "NULL")
    void aplicaFechasIgualesNuevasONulas(Integer almacenada, Integer entrante) {
        Instant fechaAlmacenada = almacenada == null ? null : FECHA.plusSeconds(almacenada);
        Instant fechaEntrante = entrante == null ? null : FECHA.plusSeconds(entrante);
        var replica = existente(fechaAlmacenada);
        when(replicas.findById("entidad")).thenReturn(Optional.of(replica));
        listener.alRecibir(mensaje("ClienteActualizado", fechaEntrante));
        verify(replicas).save(replica);
        assertThat(replica.getNombre()).isEqualTo("Nuevo");
        assertThat(replica.getFechaActualizacion()).isEqualTo(fechaEntrante);
        verificarRegistro("ClienteActualizado");
    }

    @Test
    void propagaErrorDePersistenciaSinRegistrarEvento() {
        when(replicas.findById("entidad")).thenReturn(Optional.empty());
        var fallo = new IllegalStateException("Fallo temporal");
        when(replicas.save(any())).thenThrow(fallo);
        assertThatThrownBy(() -> listener.alRecibir(mensaje("ClienteCreado", FECHA))).isSameAs(fallo);
        verify(eventos, never()).save(any());
    }

    private ClienteReplica existente(Instant fecha) {
        return ClienteReplica.crear("entidad", "Anterior", fecha);
    }

    private Message mensaje(String tipo, Instant fecha) {
        String fechaJson = fecha == null ? "null" : "\"" + fecha + "\"";
        return json("{\"eventoId\":\"evento\",\"tipo\":\"" + tipo + "\",\"datos\":{"
                + "\"clienteId\":\"entidad\",\"nombre\":\"Nuevo\","
                + "\"correo\":\"privado@example.test\",\"telefono\":\"123\",\"direccion\":\"Privada\","
                + "\"fechaActualizacion\":" + fechaJson + "}}");
    }

    private Message json(String contenido) {
        return new Message(contenido.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    private void verificarRegistro(String tipo) {
        var captor = ArgumentCaptor.forClass(EventoProcesado.class);
        verify(eventos).save(captor.capture());
        assertThat(captor.getValue().getEventoId()).isEqualTo("evento");
        assertThat(captor.getValue().getTipo()).isEqualTo(tipo);
        assertThat(captor.getValue().getFechaProcesado()).isNotNull();
    }
}
