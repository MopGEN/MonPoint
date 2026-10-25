package com.monpoint.ventas.listener;

import com.monpoint.ventas.model.ProductoReplica;
import com.monpoint.ventas.model.EventoProcesado;
import com.monpoint.ventas.repository.ProductoReplicaRepository;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoEventoListenerTest {
    private static final Instant FECHA = Instant.parse("2026-10-25T12:00:00Z");
    @Mock ProductoReplicaRepository replicas;
    @Mock EventoProcesadoRepository eventos;
    private ProductoEventoListener listener;

    @BeforeEach
    void preparar() {
        listener = new ProductoEventoListener(JsonMapper.builder().build(), replicas, eventos);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ProductoCreado", "ProductoActualizado", "ProductoDadoDeBaja"})
    void creaReplicaYRegistraEvento(String tipo) {
        when(replicas.findById("entidad")).thenReturn(Optional.empty());
        listener.alRecibir(mensaje(tipo, FECHA));
        var captor = ArgumentCaptor.forClass(ProductoReplica.class);
        verify(replicas).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("entidad");
        assertThat(captor.getValue().getNombre()).isEqualTo("Nuevo");
        assertThat(captor.getValue().getFechaActualizacion()).isEqualTo(FECHA);
        assertThat(captor.getValue().getPrecio()).isEqualByComparingTo("12.34");
        assertThat(captor.getValue().isActivo()).isEqualTo(!tipo.equals("ProductoDadoDeBaja"));
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
        listener.alRecibir(mensaje("ProductoActualizado", FECHA.minusSeconds(1)));
        assertThat(replica.getNombre()).isEqualTo("Anterior");
        assertThat(replica.getFechaActualizacion()).isEqualTo(FECHA);
        verify(replicas, never()).save(any());
        verificarRegistro("ProductoActualizado");
    }

    @ParameterizedTest
    @CsvSource(value = {"0,0", "0,1", "0,NULL", "NULL,0", "NULL,NULL"}, nullValues = "NULL")
    void aplicaFechasIgualesNuevasONulas(Integer almacenada, Integer entrante) {
        Instant fechaAlmacenada = almacenada == null ? null : FECHA.plusSeconds(almacenada);
        Instant fechaEntrante = entrante == null ? null : FECHA.plusSeconds(entrante);
        var replica = existente(fechaAlmacenada);
        when(replicas.findById("entidad")).thenReturn(Optional.of(replica));
        listener.alRecibir(mensaje("ProductoActualizado", fechaEntrante));
        verify(replicas).save(replica);
        assertThat(replica.getNombre()).isEqualTo("Nuevo");
        assertThat(replica.getFechaActualizacion()).isEqualTo(fechaEntrante);
        verificarRegistro("ProductoActualizado");
    }

    @Test
    void propagaErrorDePersistenciaSinRegistrarEvento() {
        when(replicas.findById("entidad")).thenReturn(Optional.empty());
        var fallo = new IllegalStateException("Fallo temporal");
        when(replicas.save(any())).thenThrow(fallo);
        assertThatThrownBy(() -> listener.alRecibir(mensaje("ProductoCreado", FECHA))).isSameAs(fallo);
        verify(eventos, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"activo\":null"})
    void rechazaActivoAusenteONulo(String campo) {
        var mensaje = json("{\"eventoId\":\"evento\",\"datos\":{\"productoId\":\"entidad\","
                + "\"nombre\":\"Nuevo\",\"precio\":12.34" + campo + "}}");
        assertThatThrownBy(() -> listener.alRecibir(mensaje))
                .isInstanceOf(NullPointerException.class).hasMessage("El campo activo es obligatorio");
        verifyNoInteractions(replicas);
        verify(eventos, never()).save(any());
    }

    private ProductoReplica existente(Instant fecha) {
        return ProductoReplica.crear("entidad", "Anterior", new BigDecimal("10.00"), true, fecha);
    }

    private Message mensaje(String tipo, Instant fecha) {
        String fechaJson = fecha == null ? "null" : "\"" + fecha + "\"";
        return json("{\"eventoId\":\"evento\",\"tipo\":\"" + tipo + "\",\"datos\":{"
                + "\"productoId\":\"entidad\",\"nombre\":\"Nuevo\","
                + "\"precio\":12.34,\"existencia\":999,\"activo\":" + !tipo.equals("ProductoDadoDeBaja") + ","
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
