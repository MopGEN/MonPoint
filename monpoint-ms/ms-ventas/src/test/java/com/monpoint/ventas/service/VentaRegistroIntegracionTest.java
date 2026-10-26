package com.monpoint.ventas.service;

import com.monpoint.ventas.dto.request.VentaRequest;
import com.monpoint.ventas.dto.response.VentaResponse;
import com.monpoint.ventas.event.impl.RabbitVentaEventPublisher;
import com.monpoint.ventas.model.*;
import com.monpoint.ventas.repository.*;
import com.monpoint.ventas.security.UsuarioActual;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Transacciones y contención reales. Solo el transporte Rabbit se sustituye; el adaptador es real. */
@SpringBootTest(properties = {
        "spring.mongodb.uri=${MONGO_VENTAS_TEST_URI}", "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.dynamic=false"
})
@EnabledIfEnvironmentVariable(named = "MONGO_VENTAS_TEST_URI", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VentaRegistroIntegracionTest {
    @Autowired VentaService servicio;
    @Autowired ProductoReplicaRepository productos;
    @Autowired ClienteReplicaRepository clientes;
    @Autowired VentaRepository ventas;
    @Autowired ContadorRepository contadores;
    @Autowired MongoTemplate mongo;
    @Autowired JsonMapper json;
    @MockitoBean RabbitTemplate rabbit;
    @MockitoSpyBean RabbitVentaEventPublisher publicador;
    private final String productoId = new ObjectId().toHexString();
    private final String clienteId = new ObjectId().toHexString();
    private static final UsuarioActual VENDEDOR = new UsuarioActual("prueba-registro", "VENDEDOR");

    @BeforeEach void prepararReplica() {
        productos.save(ProductoReplica.crear(productoId, "Tinta histórica", new BigDecimal("41.15"), true, Instant.now()));
        clientes.save(ClienteReplica.crear(clienteId, "Cliente histórico", Instant.now()));
    }

    @AfterEach void limpiarSoloMisDocumentos() {
        mongo.remove(Query.query(Criteria.where("lineas.productoId").is(new ObjectId(productoId))), Venta.class);
        productos.deleteById(productoId);
        clientes.deleteById(clienteId);
        // No se rebobina el contador compartido: hacerlo podría reutilizar folios de otras ventas.
    }

    @Test @Order(1)
    void ochoVentasConcurrentesRecibenFoliosDistintosYPublicanSoloDespuesDePersistir() throws Exception {
        long inicial = folioActual();
        var publicados = new ConcurrentLinkedQueue<String>();
        doAnswer(inv -> {
            Message mensaje = inv.getArgument(2);
            var datos = json.readTree(mensaje.getBody()).get("datos");
            String id = datos.get("ventaId").asText();
            // Consulta nativa sin ClientSession: no puede ver una venta sin confirmar.
            var persistida = mongo.getCollection("ventas").find(new Document("_id", new ObjectId(id))).first();
            assertThat(persistida).isNotNull();
            assertThat(persistida.getLong("folio")).isEqualTo(datos.get("folio").asLong());
            publicados.add(id);
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class));
        var listos = new CountDownLatch(8);
        var salida = new CountDownLatch(1);
        var respuestas = new ArrayList<VentaResponse>();
        try (var ejecutor = Executors.newFixedThreadPool(8)) {
            var futuros = new ArrayList<Future<VentaResponse>>();
            for (int i = 0; i < 8; i++) futuros.add(ejecutor.submit(() -> {
                listos.countDown();
                if (!salida.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No se liberó la salida");
                return servicio.registrar(solicitud(null), VENDEDOR);
            }));
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue();
            salida.countDown();
            for (var futuro : futuros) respuestas.add(futuro.get(30, TimeUnit.SECONDS));
        }
        assertThat(respuestas.stream().map(VentaResponse::folio).sorted().toList())
                .containsExactlyElementsOf(LongStream.rangeClosed(inicial + 1, inicial + 8).boxed().toList());
        assertThat(publicados).hasSize(8).doesNotHaveDuplicates();
        assertThat(respuestas).allSatisfy(r -> assertThat(r.estado()).isEqualTo(EstadoVenta.PENDIENTE));
        assertThat(folioActual()).isEqualTo(inicial + 8);
    }

    @Test @Order(2)
    void falloTrasGuardarYProgramarEventoRevierteVentaYFolioSinPublicar() {
        long inicial = folioActual();
        doAnswer(inv -> {
            inv.callRealMethod(); // Registra realmente afterCommit, después de insertar la venta.
            throw new IllegalStateException("Fallo antes del commit");
        }).when(publicador).publicarVentaCreada(anyString(), anyLong(), any(), any(), any(), any(), anyList());
        assertThatThrownBy(() -> servicio.registrar(solicitud(null), VENDEDOR))
                .isInstanceOf(IllegalStateException.class).hasMessage("Fallo antes del commit");
        assertThat(folioActual()).isEqualTo(inicial);
        assertThat(mongo.count(Query.query(Criteria.where("lineas.productoId").is(new ObjectId(productoId))), Venta.class))
                .isZero();
        verifyNoInteractions(rabbit);
    }

    @Test @Order(3)
    void congelaClienteYProductosYGuardaImportesComoDecimal128SinSubtotal() {
        var respuesta = servicio.registrar(solicitud(clienteId), VENDEDOR);
        var producto = productos.findById(productoId).orElseThrow();
        producto.actualizarDesde("Nombre nuevo", new BigDecimal("999"), false, Instant.now());
        productos.save(producto);
        var cliente = clientes.findById(clienteId).orElseThrow();
        cliente.actualizarDesde("Cliente nuevo", Instant.now());
        clientes.save(cliente);
        var venta = ventas.findById(respuesta.id()).orElseThrow();
        assertThat(venta.getClienteNombre()).isEqualTo("Cliente histórico");
        assertThat(venta.getLineas().getFirst().nombreProducto()).isEqualTo("Tinta histórica");
        assertThat(venta.getTotal()).isEqualByComparingTo("123.45");
        assertThatThrownBy(() -> venta.getLineas().clear()).isInstanceOf(UnsupportedOperationException.class);
        var documento = mongo.getCollection("ventas").find(new Document("_id", new ObjectId(respuesta.id()))).first();
        assertThat(documento.get("total")).isInstanceOf(Decimal128.class);
        assertThat(documento.get("clienteId")).isInstanceOf(ObjectId.class);
        assertThat(documento.get("folio")).isInstanceOf(Long.class);
        var linea = documento.getList("lineas", Document.class).getFirst();
        assertThat(linea.get("productoId")).isInstanceOf(ObjectId.class);
        assertThat(linea.get("precioUnitario")).isInstanceOf(Decimal128.class);
        assertThat(linea).doesNotContainKey("subtotal");
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("ventas.eventos"), eq("venta.creada"), captor.capture());
        var datos = json.readTree(captor.getValue().getBody()).get("datos");
        assertThat(datos.get("clienteNombre").asText()).isEqualTo("Cliente histórico");
        assertThat(datos.has("nombreCliente")).isFalse();
        assertThat(datos.get("lineas").get(0).has("subtotal")).isFalse();
    }

    @Test @Order(4)
    void brokerCaidoConservaVentaPendienteYRespuestaExitosa() {
        doThrow(new AmqpException("Broker no disponible")).when(rabbit).send(anyString(), anyString(), any(Message.class));
        var respuesta = servicio.registrar(solicitud(null), VENDEDOR);
        assertThat(respuesta.estado()).isEqualTo(EstadoVenta.PENDIENTE);
        var venta = ventas.findById(respuesta.id()).orElseThrow();
        assertThat(venta.getClienteId()).isNull();
        assertThat(venta.getClienteNombre()).isNull();
        verify(rabbit).send(anyString(), anyString(), any(Message.class));
    }

    private long folioActual() {
        return contadores.findById(Contador.VENTA_FOLIO).map(Contador::getValor).orElse(0L);
    }
    private VentaRequest solicitud(String cliente) {
        return new VentaRequest(cliente, List.of(new VentaRequest.LineaRequest(productoId, 3)));
    }
}
