package com.monpoint.notificaciones;

import com.monpoint.notificaciones.event.StockBajo;
import com.monpoint.notificaciones.model.*;
import com.monpoint.notificaciones.security.UsuarioActual;
import com.monpoint.notificaciones.service.NotificacionService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mongo real con replica set. Cada prueba elimina solo sus propios documentos. */
@SpringBootTest(properties = {
        "spring.mongodb.uri=${MONGO_NOTIFICACIONES_TEST_URI}",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "MONGO_NOTIFICACIONES_TEST_URI", matches = ".+")
class NotificacionesIntegracionTest {
    @Autowired NotificacionService servicio;
    @Autowired MockMvc http;
    @Autowired org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitFactory;
    @MockitoSpyBean MongoTemplate mongo;
    private final String producto = new ObjectId().toHexString();
    private final List<String> eventos = new ArrayList<>();
    private static final UsuarioActual ADMIN = new UsuarioActual("admin-prueba", Rol.ADMIN);

    /** Misma posición de argumentos que invokeListener(Channel, Message). */
    public interface Entrega { void recibir(Object canal, org.springframework.amqp.core.Message mensaje); }

    @Test
    void configuracionRealDeBootEntregaTresVecesYRechazaSinReencolar() {
        var intentos = new java.util.concurrent.atomic.AtomicInteger();
        Entrega falla = (canal, mensaje) -> {
            intentos.incrementAndGet();
            throw new IllegalStateException("Mensaje inválido de prueba");
        };
        var proxy = new org.springframework.aop.framework.ProxyFactory(falla);
        assertThat(rabbitFactory.getAdviceChain()).isNotEmpty();
        for (var advice : rabbitFactory.getAdviceChain()) proxy.addAdvice(advice);
        var entrega = (Entrega) proxy.getProxy();
        var mensaje = new org.springframework.amqp.core.Message(new byte[0]);
        assertThatThrownBy(() -> entrega.recibir(null, mensaje))
                .hasCauseInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(intentos).hasValue(3);
    }

    @AfterEach
    void limpiarSoloEstaPrueba() {
        reset(mongo);
        mongo.remove(Query.query(Criteria.where("productoId").is(producto)), Notificacion.class);
        mongo.remove(Query.query(Criteria.where("eventoId").in(eventos)), EventoProcesado.class);
    }

    @Test
    void creaNoLeidaConNombreExistenciaYFechaSinCambiarElContrato() {
        Instant antes = Instant.now().minusSeconds(1);
        servicio.recibir(evento(5));
        var n = unica();
        assertThat(n.productoId()).isEqualTo(producto);
        assertThat(n.productoNombre()).isEqualTo("Tinta de prueba");
        assertThat(n.tipo()).isEqualTo("STOCK_BAJO");
        assertThat(n.existenciaResultante()).isEqualTo(5);
        assertThat(n.mensaje()).contains("Tinta de prueba", "5");
        assertThat(n.activa()).isTrue();
        assertThat(n.leida()).isFalse();
        assertThat(n.fechaLectura()).isNull();
        assertThat(n.fechaCreacion()).isBetween(antes, Instant.now());
    }

    @Test
    void eventosDistintosActualizanLaActivaYConservanIdentidadYFechaCreacion() {
        servicio.recibir(evento(5));
        var anterior = unica();
        servicio.recibir(evento(3));
        var actual = unica();
        assertThat(actual.id()).isEqualTo(anterior.id());
        assertThat(actual.fechaCreacion()).isEqualTo(anterior.fechaCreacion());
        assertThat(actual.existenciaResultante()).isEqualTo(3);
        assertThat(actual.leida()).isFalse();
        assertThat(procesados()).isEqualTo(2);
    }

    @Test
    void reentregaDespuesDeLeerNoReabreNiCambiaFechaDeLectura() {
        var e = evento(4);
        servicio.recibir(e);
        servicio.leer(unica().id(), ADMIN);
        var cerrada = unica();
        servicio.recibir(e);
        servicio.leer(cerrada.id(), ADMIN);
        assertThat(unica()).isEqualTo(cerrada);
        assertThat(cerrada.activa()).isFalse();
        assertThat(cerrada.leida()).isTrue();
        assertThat(cerrada.fechaLectura()).isNotNull();
        assertThat(procesados()).isEqualTo(1);
    }

    @Test
    void permiteVariasInactivasYUnaNuevaActivaDelMismoProducto() {
        for (int i = 0; i < 2; i++) {
            servicio.recibir(evento(5 - i));
            servicio.leer(avisos().stream().filter(Notificacion::activa).findFirst().orElseThrow().id(), ADMIN);
        }
        servicio.recibir(evento(2));
        assertThat(avisos()).hasSize(3);
        assertThat(avisos().stream().filter(Notificacion::activa)).hasSize(1);
        assertThat(avisos().stream().filter(Notificacion::leida)).hasSize(2);
    }

    @Test
    void indiceParcialEsLaGarantiaYElCompuestoNoImpideHistoricos() {
        var indices = mongo.getCollection("notificaciones").listIndexes().into(new ArrayList<>());
        var parcial = indices.stream().filter(i -> "producto_activo_unico".equals(i.getString("name"))).findFirst().orElseThrow();
        assertThat(parcial.getBoolean("unique")).isTrue();
        assertThat(parcial.get("key", Document.class)).isEqualTo(new Document("productoId", 1));
        assertThat(parcial.get("partialFilterExpression", Document.class)).isEqualTo(new Document("activa", true));
        var compuesto = indices.stream().filter(i -> "producto_activa".equals(i.getString("name"))).findFirst().orElseThrow();
        assertThat(compuesto.get("key", Document.class)).isEqualTo(new Document("productoId", 1).append("activa", 1));
        assertThat(compuesto.getBoolean("unique", false)).isFalse();
        servicio.recibir(evento(5));
        var n = unica();
        assertThatThrownBy(() -> mongo.insert(new Notificacion(null, n.tipo(), producto,
                n.productoNombre(), 4, "Duplicada", true, false, Instant.now(), null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void doceEventosConcurrentesDelMismoProductoDejanUnaActivaYSusDoceMarcadores() throws Exception {
        var mensajes = IntStream.range(0, 12).mapToObj(i -> evento(i % 6)).toList();
        concurrentes(mensajes.stream().<Runnable>map(e -> () -> servicio.recibir(e)).toList());
        assertThat(avisos()).hasSize(1);
        assertThat(unica().activa()).isTrue();
        assertThat(procesados()).isEqualTo(12);
    }

    @Test
    void doceReentregasConcurrentesTienenUnSoloEfecto() throws Exception {
        var e = evento(4);
        concurrentes(IntStream.range(0, 12).<Runnable>mapToObj(i -> () -> servicio.recibir(e)).toList());
        assertThat(avisos()).hasSize(1);
        assertThat(procesados()).isEqualTo(1);
    }

    @Test
    void lecturaConcurrenteConEventoNoPierdeElEventoNiCreaDosActivas() throws Exception {
        servicio.recibir(evento(5));
        var id = unica().id();
        var siguiente = evento(4);
        concurrentes(List.of(() -> servicio.leer(id, ADMIN), () -> servicio.recibir(siguiente)));
        assertThat(procesados()).isEqualTo(2);
        assertThat(avisos().stream().filter(Notificacion::activa).count()).isLessThanOrEqualTo(1);
        assertThat(avisos()).anyMatch(n -> n.existenciaResultante() == 4);
        assertThat(avisos().stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow().leida()).isTrue();
    }

    @Test
    void falloAlGuardarAvisoRevierteMarcadorYPermiteReentregar() {
        var e = evento(5);
        doThrow(new IllegalStateException("Fallo de escritura inducido"))
                .when(mongo).findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Notificacion.class));
        assertThatThrownBy(() -> servicio.recibir(e)).isInstanceOf(IllegalStateException.class);
        assertThat(procesados()).isZero();
        assertThat(avisos()).isEmpty();
        reset(mongo);
        servicio.recibir(e);
        assertThat(avisos()).hasSize(1);
        assertThat(procesados()).isEqualTo(1);
    }

    @Test
    void fechaDelSobreNoSeInterpretaComoVersionCausal() {
        servicio.recibir(evento(2));
        var viejo = evento(5);
        servicio.recibir(new StockBajo(viejo.eventoId(), viejo.tipo(), viejo.version(),
                "2020-01-01T00:00:00Z", viejo.origen(), viejo.datos()));
        assertThat(unica().existenciaResultante()).isEqualTo(5);
    }

    @Test
    void apiFiltraLecturaYLeerDevuelve204ConFechaEstable() throws Exception {
        servicio.recibir(evento(5));
        var id = unica().id();
        http.perform(get("/notificaciones").header("X-User-Id", "admin").header("X-User-Role", "ADMIN").param("leida", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].leida").value(org.hamcrest.Matchers.contains(false)));
        http.perform(put("/notificaciones/{id}/leida", id).header("X-User-Id", "admin").header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        var leida = unica();
        http.perform(put("/notificaciones/{id}/leida", id).header("X-User-Id", "admin").header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
        assertThat(unica()).isEqualTo(leida);
        http.perform(get("/notificaciones").header("X-User-Id", "admin").header("X-User-Role", "ADMIN").param("leida", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());
        http.perform(get("/notificaciones").header("X-User-Id", "admin").header("X-User-Role", "ADMIN").param("leida", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == '" + id + "')].leida").value(org.hamcrest.Matchers.contains(true)));
    }

    @Test
    void apiExigeAdminEnAmbasRutasYDevuelveErroresConRequestId() throws Exception {
        for (var peticion : List.of(get("/notificaciones"), put("/notificaciones/" + new ObjectId() + "/leida"))) {
            http.perform(peticion).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        for (var peticion : List.of(get("/notificaciones"), put("/notificaciones/" + new ObjectId() + "/leida"))) {
            http.perform(peticion.header("X-User-Id", "vendedor").header("X-User-Role", "VENDEDOR"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        for (var id : List.of(new ObjectId().toHexString(), "invalido")) {
            http.perform(put("/notificaciones/{id}/leida", id).header("X-User-Id", "admin").header("X-User-Role", "ADMIN"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.requestId").isNotEmpty());
        }
        http.perform(get("/notificaciones").header("X-User-Id", "admin").header("X-User-Role", "ADMIN").param("leida", "invalido"))
                .andExpect(status().isBadRequest());
    }

    private StockBajo evento(int stock) {
        String id = UUID.randomUUID().toString();
        eventos.add(id);
        return new StockBajo(id, "StockBajo", "1", Instant.now().toString(), "ms-inventario",
                new StockBajo.Datos(producto, "Tinta de prueba", stock));
    }
    private List<Notificacion> avisos() {
        return mongo.find(Query.query(Criteria.where("productoId").is(producto)), Notificacion.class);
    }
    private Notificacion unica() { assertThat(avisos()).hasSize(1); return avisos().getFirst(); }
    private long procesados() { return mongo.count(Query.query(Criteria.where("eventoId").in(eventos)), EventoProcesado.class); }
    private static void concurrentes(List<Runnable> tareas) throws Exception {
        try (var pool = Executors.newFixedThreadPool(tareas.size())) {
            var inicio = new CountDownLatch(1);
            var listas = new CountDownLatch(tareas.size());
            var futuros = tareas.stream().map(t -> pool.submit(() -> {
                listas.countDown();
                if (!inicio.await(10, TimeUnit.SECONDS)) throw new TimeoutException("Barrera de inicio");
                t.run(); return null;
            })).toList();
            try { assertThat(listas.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { inicio.countDown(); }
            for (var f : futuros) f.get(30, TimeUnit.SECONDS);
        }
    }
}
