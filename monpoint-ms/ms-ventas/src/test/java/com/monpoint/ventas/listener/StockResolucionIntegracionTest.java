package com.monpoint.ventas.listener;

import com.monpoint.ventas.event.SobreEvento;
import com.monpoint.ventas.model.*;
import com.monpoint.ventas.repository.*;
import com.monpoint.ventas.service.FolioService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** MongoDB y proxy transaccional reales; únicamente se sustituye el transporte Rabbit. */
@SpringBootTest(properties = {
        "spring.mongodb.uri=${MONGO_VENTAS_TEST_URI}", "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"
})
@EnabledIfEnvironmentVariable(named = "MONGO_VENTAS_TEST_URI", matches = ".+")
class StockResolucionIntegracionTest {
    @Autowired StockEventoListener listener;
    @Autowired FolioService folios;
    @Autowired MongoTemplate mongo;
    @Autowired JsonMapper json;
    @MockitoSpyBean VentaRepository ventas;
    @MockitoSpyBean EventoProcesadoRepository eventos;
    @MockitoBean RabbitTemplate rabbit;
    private final String productoId = new ObjectId().toHexString();
    private final String eventoId = "cierre-prueba-" + UUID.randomUUID();
    private final String segundoEvento = eventoId + "-segundo";
    private Venta venta;

    @BeforeEach void prepararVenta() {
        venta = Venta.pendiente(null, null,
                List.of(new LineaVenta(productoId, "Tinta histórica", new BigDecimal("41.15"), 3)),
                Instant.parse("2026-10-25T12:00:00Z"));
        venta.asignarFolio(folios.siguiente());
        ventas.insert(venta);
    }

    @AfterEach void limpiarSoloMisDocumentos() {
        if (venta != null && venta.getId() != null) ventas.deleteById(venta.getId());
        mongo.remove(Query.query(Criteria.where("eventoId").in(eventoId, segundoEvento)), EventoProcesado.class);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void resuelveUnaVezPublicaDespuesDelCommitEIgnoraRespuestaContraria(boolean confirmar) {
        doAnswer(inv -> {
            Message salida = inv.getArgument(2);
            var sobre = json.readTree(salida.getBody());
            var datos = sobre.get("datos");
            // Consultas sin ClientSession: ambos documentos deben ser visibles fuera de la transacción.
            var persistida = mongo.getCollection("ventas").find(new Document("_id", new ObjectId(venta.getId()))).first();
            assertThat(persistida.getString("estado")).isEqualTo(confirmar ? "CONFIRMADA" : "CANCELADA");
            assertThat(mongo.getCollection("eventos_procesados").find(new Document("eventoId", eventoId)).first())
                    .isNotNull();
            assertThat(datos.get("fechaResolucion").asText())
                    .isEqualTo(persistida.getDate("fechaResolucion").toInstant().toString());
            assertThat(datos.get("lineas").get(0).get("nombreProducto").asText()).isEqualTo("Tinta histórica");
            assertThat(datos.get("lineas").get(0).has("subtotal")).isFalse();
            assertThat(datos.get("total").decimalValue()).isEqualByComparingTo("123.45");
            assertThat(datos.get("clienteId").isNull()).isTrue();
            assertThat(datos.has("motivo")).isEqualTo(!confirmar);
            if (!confirmar) assertThat(datos.get("motivo").asText()).isEqualTo("EXISTENCIA_INSUFICIENTE");
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class));
        listener.alRecibir(mensaje(eventoId, confirmar, venta.getId()));
        var resuelta = ventas.findById(venta.getId()).orElseThrow();
        listener.alRecibir(mensaje(eventoId, confirmar, venta.getId()));
        listener.alRecibir(mensaje(segundoEvento, !confirmar, venta.getId()));
        var finalizada = ventas.findById(venta.getId()).orElseThrow();
        assertThat(finalizada.getEstado()).isEqualTo(resuelta.getEstado());
        assertThat(finalizada.getFechaResolucion()).isEqualTo(resuelta.getFechaResolucion());
        assertThat(finalizada.getMotivoCancelacion()).isEqualTo(confirmar ? null
                : "existencia insuficiente de Tinta histórica, solicitado 3");
        assertThat(eventos.existsByEventoId(eventoId)).isTrue();
        assertThat(eventos.existsByEventoId(segundoEvento)).isTrue();
        verify(rabbit).send(eq("ventas.eventos"), eq(confirmar ? "venta.confirmada" : "venta.cancelada"), any(Message.class));
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void indiceUnicoRevierteResolucionYCallbackSiOtraEntregaRegistroElEvento(boolean confirmar) {
        eventos.save(EventoProcesado.de(eventoId, "Original", Instant.now()));
        doReturn(false).when(eventos).existsByEventoId(eventoId);
        assertThatThrownBy(() -> listener.alRecibir(mensaje(eventoId, confirmar, venta.getId())))
                .isInstanceOf(DuplicateKeyException.class);
        var intacta = ventas.findById(venta.getId()).orElseThrow();
        assertThat(intacta.getEstado()).isEqualTo(EstadoVenta.PENDIENTE);
        assertThat(intacta.getFechaResolucion()).isNull();
        assertThat(intacta.getMotivoCancelacion()).isNull();
        assertThat(mongo.count(Query.query(Criteria.where("eventoId").is(eventoId)), EventoProcesado.class)).isEqualTo(1);
        verifyNoInteractions(rabbit);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void ventaInexistentePropagaYSigueSinRegistrarEvento(boolean confirmar) {
        assertThatThrownBy(() -> listener.alRecibir(mensaje(eventoId, confirmar, new ObjectId().toHexString())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no existe");
        assertThat(eventos.existsByEventoId(eventoId)).isFalse();
        verifyNoInteractions(rabbit);
    }

    @Test void dosDesenlacesConcurrentesSoloPublicanUnoYLaReentregaRegistraElOtro() throws Exception {
        var lecturas = new CountDownLatch(2);
        var numeroLectura = new AtomicInteger();
        doAnswer(inv -> {
            var resultado = Optional.ofNullable(mongo.findById(venta.getId(), Venta.class));
            if (numeroLectura.incrementAndGet() <= 2) {
                lecturas.countDown();
                if (!lecturas.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No coincidieron las lecturas");
            }
            return resultado;
        }).when(ventas).findById(venta.getId());
        var confirmado = mensaje(eventoId, true, venta.getId());
        var rechazado = mensaje(segundoEvento, false, venta.getId());
        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var uno = ejecutor.submit(() -> intentar(confirmado));
            var dos = ejecutor.submit(() -> intentar(rechazado));
            RuntimeException falloUno = uno.get(20, TimeUnit.SECONDS);
            RuntimeException falloDos = dos.get(20, TimeUnit.SECONDS);
            assertThat((falloUno == null) != (falloDos == null))
                    .as("Solo una transacción debe fallar: %s / %s", falloUno, falloDos).isTrue();
            // Spring AMQP reentrega después del conflicto. La siguiente transacción ve la venta resuelta.
            listener.alRecibir(falloUno == null ? rechazado : confirmado);
        }
        assertThat(ventas.findById(venta.getId()).orElseThrow().getEstado()).isNotEqualTo(EstadoVenta.PENDIENTE);
        assertThat(eventos.existsByEventoId(eventoId)).isTrue();
        assertThat(eventos.existsByEventoId(segundoEvento)).isTrue();
        verify(rabbit).send(anyString(), anyString(), any(Message.class));
    }

    @Test void falloDelBrokerNoRevierteLaVentaYaConfirmada() {
        doThrow(new AmqpException("Broker caído")).when(rabbit).send(anyString(), anyString(), any(Message.class));
        listener.alRecibir(mensaje(eventoId, true, venta.getId()));
        assertThat(ventas.findById(venta.getId()).orElseThrow().getEstado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(eventos.existsByEventoId(eventoId)).isTrue();
        listener.alRecibir(mensaje(eventoId, true, venta.getId()));
        verify(rabbit).send(anyString(), anyString(), any(Message.class));
    }

    private RuntimeException intentar(Message mensaje) {
        try { listener.alRecibir(mensaje); return null; }
        catch (RuntimeException ex) { return ex; }
    }

    private Message mensaje(String id, boolean confirmar, String ventaId) {
        Map<String, Object> datos = confirmar ? Map.of("ventaId", ventaId)
                : Map.of("ventaId", ventaId, "productoId", productoId, "motivo", "EXISTENCIA_INSUFICIENTE");
        var sobre = new SobreEvento(id, confirmar ? "StockReservado" : "StockRechazado", "1",
                Instant.now().toString(), "ms-inventario", datos);
        return new Message(json.writeValueAsBytes(sobre), new MessageProperties());
    }
}
