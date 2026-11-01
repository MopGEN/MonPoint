package com.monpoint.ventas.listener;

import com.monpoint.ventas.event.*;
import com.monpoint.ventas.model.*;
import com.monpoint.ventas.repository.*;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockEventoListenerTest {
    private static final String ID = "507f1f77bcf86cd799439011";
    private static final String PRODUCTO = "507f1f77bcf86cd799439012";
    private static final Instant FECHA = Instant.parse("2026-10-25T12:00:00Z");
    private final JsonMapper json = JsonMapper.builder().build();
    @Mock VentaRepository ventas;
    @Mock EventoProcesadoRepository eventos;
    @Mock VentaEventPublisher publicador;

    @Test void duplicadoNoLeeVentaNiPublicaAunqueNoTengaDatos() {
        when(eventos.existsByEventoId("evento")).thenReturn(true);
        recibir("StockReservado", Map.of());
        verifyNoInteractions(ventas, publicador);
        verify(eventos, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"StockReservado", "StockRechazado"})
    void ventaAusentePropagaSinMarcarEvento(String tipo) {
        when(ventas.findById(ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> recibir(tipo, Map.of("ventaId", ID)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no existe");
        verify(eventos, never()).save(any());
        verifyNoInteractions(publicador);
    }

    @ParameterizedTest
    @CsvSource({"StockReservado,true", "StockReservado,false", "StockRechazado,true", "StockRechazado,false"})
    void ventaResueltaRegistraSinModificarNiVolverAPublicar(String tipo, boolean confirmada) {
        var venta = pendiente();
        if (confirmada) venta.confirmar(FECHA);
        else venta.cancelar("Motivo anterior", PRODUCTO, FECHA);
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));
        // Un cierre tardío no necesita reconstruir su motivo: la decisión ya es ignorarlo.
        recibir(tipo, Map.of("ventaId", ID));
        assertThat(venta.getEstado()).isEqualTo(confirmada ? EstadoVenta.CONFIRMADA : EstadoVenta.CANCELADA);
        assertThat(venta.getFechaResolucion()).isEqualTo(FECHA);
        assertThat(venta.getMotivoCancelacion()).isEqualTo(confirmada ? null : "Motivo anterior");
        verify(ventas, never()).save(any());
        verify(eventos).save(any(EventoProcesado.class));
        verifyNoInteractions(publicador);
    }

    @Test void confirmaConSnapshotCompletoSinAlmacenarExistenciaResultante() {
        var venta = pendiente();
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));
        recibir("StockReservado", Map.of("ventaId", ID, "lineas",
                List.of(Map.of("productoId", PRODUCTO, "cantidad", 3, "existenciaResultante", 999))));
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(venta.getFechaResolucion()).isNotNull();
        assertThat(venta.getMotivoCancelacion()).isNull();
        var salida = ArgumentCaptor.forClass(VentaEventPublisher.VentaPublicada.class);
        var orden = inOrder(ventas, publicador, eventos);
        orden.verify(ventas).save(venta);
        orden.verify(publicador).publicarVentaConfirmada(salida.capture());
        orden.verify(eventos).save(any(EventoProcesado.class));
        assertThat(salida.getValue().ventaId()).isEqualTo(ID);
        assertThat(salida.getValue().folio()).isEqualTo(42);
        assertThat(salida.getValue().clienteNombre()).isEqualTo("Ana histórica");
        assertThat(salida.getValue().fechaCreacion()).isEqualTo(FECHA);
        assertThat(salida.getValue().fechaResolucion()).isEqualTo(venta.getFechaResolucion());
        assertThat(salida.getValue().total()).isEqualByComparingTo("123.45");
        assertThat(salida.getValue().lineas()).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "EXISTENCIA_INSUFICIENTE|existencia insuficiente de Tinta para sello, solicitado 3",
            "PRODUCTO_INACTIVO|producto inactivo: Tinta para sello",
            "PRODUCTO_NO_ENCONTRADO|producto no disponible en inventario: Tinta para sello"})
    void cancelaConNombreHistoricoYCantidadAgrupadaYPublicaCodigo(String codigo, String frase) {
        var venta = pendiente();
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));
        recibir("StockRechazado", Map.of("ventaId", ID, "productoId", PRODUCTO, "motivo", codigo,
                "solicitado", 999, "nombreProducto", "Nombre ajeno"));
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.CANCELADA);
        assertThat(venta.getMotivoCancelacion()).isEqualTo(frase);
        // El identificador se guarda junto a la frase para que la caja senale la linea del carrito
        // (actividad 5.5, paso 9) sin tener que analizar el texto del motivo.
        assertThat(venta.getProductoIdRechazado()).isEqualTo(PRODUCTO);
        assertThat(venta.getFechaResolucion()).isNotNull();
        verify(publicador).publicarVentaCancelada(any(), eq(codigo));
        verify(eventos).save(any(EventoProcesado.class));
    }

    @ParameterizedTest @ValueSource(strings = {"desconocido", "", "StockBajo"})
    void tipoNoSoportadoPropaga(String tipo) {
        assertThatThrownBy(() -> recibir(tipo, Map.of("ventaId", ID)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ventas, publicador);
        verify(eventos, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"DESCONOCIDO", ""})
    void motivoInvalidoNoResuelve(String codigo) {
        var venta = pendiente();
        when(ventas.findById(ID)).thenReturn(Optional.of(venta));
        assertThatThrownBy(() -> recibir("StockRechazado", Map.of("ventaId", ID,
                "productoId", PRODUCTO, "motivo", codigo))).isInstanceOf(IllegalArgumentException.class);
        assertThat(venta.getEstado()).isEqualTo(EstadoVenta.PENDIENTE);
        verify(ventas, never()).save(any());
        verify(eventos, never()).save(any());
        verifyNoInteractions(publicador);
    }

    @Test void productoAjenoNoPuedeCancelarLaVenta() {
        when(ventas.findById(ID)).thenReturn(Optional.of(pendiente()));
        assertThatThrownBy(() -> recibir("StockRechazado", Map.of("ventaId", ID,
                "productoId", "otro", "motivo", "EXISTENCIA_INSUFICIENTE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no pertenece");
        verify(ventas, never()).save(any());
        verify(eventos, never()).save(any());
        verifyNoInteractions(publicador);
    }

    @Test void errorDeIdempotenciaSaleDelListener() {
        when(ventas.findById(ID)).thenReturn(Optional.of(pendiente()));
        when(eventos.save(any())).thenThrow(new DuplicateKeyException("Carrera"));
        assertThatThrownBy(() -> recibir("StockReservado", Map.of("ventaId", ID)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test void entidadImpideResolverDosVeces() {
        var venta = pendiente();
        venta.cancelar("Motivo", PRODUCTO, FECHA);
        assertThatThrownBy(() -> venta.confirmar(Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> venta.cancelar("Otro", PRODUCTO, Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThat(venta.getMotivoCancelacion()).isEqualTo("Motivo");
        assertThat(venta.getFechaResolucion()).isEqualTo(FECHA);
    }

    private Venta pendiente() {
        var venta = Venta.pendiente(ID, "Ana histórica", List.of(
                new LineaVenta(PRODUCTO, "Tinta para sello", new BigDecimal("41.15"), 1),
                new LineaVenta(PRODUCTO, "Tinta para sello", new BigDecimal("41.15"), 2)), FECHA);
        venta.asignarFolio(42);
        ReflectionTestUtils.setField(venta, "id", ID);
        return venta;
    }

    private void recibir(String tipo, Map<String, Object> datos) {
        var sobre = new SobreEvento("evento", tipo, "1", FECHA.toString(), "ms-inventario", datos);
        new StockEventoListener(json, ventas, eventos, publicador)
                .alRecibir(new Message(json.writeValueAsBytes(sobre), new MessageProperties()));
    }
}
