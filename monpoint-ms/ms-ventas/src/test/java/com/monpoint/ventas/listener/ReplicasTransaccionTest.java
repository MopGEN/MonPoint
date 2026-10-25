package com.monpoint.ventas.listener;

import com.monpoint.ventas.model.ClienteReplica;
import com.monpoint.ventas.model.EventoProcesado;
import com.monpoint.ventas.model.ProductoReplica;
import com.monpoint.ventas.repository.ClienteReplicaRepository;
import com.monpoint.ventas.repository.EventoProcesadoRepository;
import com.monpoint.ventas.repository.ProductoReplicaRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

/** Requiere MongoDB replica set aprovisionado con los índices de infra/mongo-init. */
@SpringBootTest(properties = {
        "spring.mongodb.uri=${MONGO_VENTAS_TEST_URI}",
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
@EnabledIfEnvironmentVariable(named = "MONGO_VENTAS_TEST_URI", matches = ".+")
class ReplicasTransaccionTest {
    private static final Instant FECHA = Instant.parse("2026-10-25T12:00:00Z");
    private final String id = "zz-prueba-replica-" + UUID.randomUUID();
    private final String eventoId = "evento-" + id;
    @Autowired ProductoEventoListener productos;
    @Autowired ClienteEventoListener clientes;
    @Autowired ProductoReplicaRepository productosRepo;
    @Autowired ClienteReplicaRepository clientesRepo;
    @Autowired MongoTemplate mongo;
    @MockitoSpyBean EventoProcesadoRepository eventos;

    @AfterEach
    void limpiarSoloDocumentosDeEstaPrueba() {
        productosRepo.deleteById(id);
        clientesRepo.deleteById(id);
        mongo.remove(Query.query(Criteria.where("eventoId").is(eventoId)), EventoProcesado.class);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "true,true", "false,false", "false,true"})
    void falloAlRegistrarEventoRevierteCreacionOActualizacion(boolean producto, boolean existente) {
        if (existente) {
            if (producto) productosRepo.save(ProductoReplica.crear(id, "Anterior", BigDecimal.TEN, true, FECHA));
            else clientesRepo.save(ClienteReplica.crear(id, "Anterior", FECHA));
        }
        eventos.save(EventoProcesado.de(eventoId, "Original", FECHA));
        // Simula que otra entrega registró el id después de nuestra comprobación.
        // La escritura real contra el índice único debe fallar y revertir la réplica.
        doReturn(false).when(eventos).existsByEventoId(eventoId);
        assertThatThrownBy(() -> recibir(producto)).isInstanceOf(DuplicateKeyException.class);

        var documento = mongo.getCollection(producto ? "productos_replica" : "clientes_replica")
                .find(new Document("_id", id)).first();
        if (existente) {
            assertThat(documento).isNotNull();
            assertThat(documento.getString("nombre")).isEqualTo("Anterior");
            assertThat(documento.getDate("fechaActualizacion").toInstant()).isEqualTo(FECHA);
        } else {
            assertThat(documento).isNull();
        }
        assertThat(mongo.count(Query.query(Criteria.where("eventoId").is(eventoId)), EventoProcesado.class))
                .isEqualTo(1);
    }

    @Test
    void persisteDecimal128SinStockYReentregaNoDuplica() {
        recibir(true);
        recibir(true);
        var documento = mongo.getCollection("productos_replica").find(new Document("_id", id)).first();
        assertThat(documento).isNotNull().doesNotContainKey("existencia");
        assertThat(documento.get("precio")).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) documento.get("precio")).bigDecimalValue()).isEqualByComparingTo("12.34");
        assertThat(documento.getBoolean("activo")).isFalse();
        assertThat(mongo.count(Query.query(Criteria.where("eventoId").is(eventoId)), EventoProcesado.class))
                .isEqualTo(1);
    }

    @Test
    void clienteNoGuardaDatosPersonalesAdicionales() {
        recibir(false);
        var documento = mongo.getCollection("clientes_replica").find(new Document("_id", id)).first();
        assertThat(documento).isNotNull().doesNotContainKeys("correo", "telefono", "direccion", "activo");
        assertThat(documento.getString("nombre")).isEqualTo("Nuevo");
        assertThat(eventos.existsByEventoId(eventoId)).isTrue();
    }

    private void recibir(boolean producto) {
        String contenido = "{\"eventoId\":\"" + eventoId + "\",\"tipo\":\""
                + (producto ? "ProductoDadoDeBaja" : "ClienteActualizado") + "\",\"datos\":{\""
                + (producto ? "productoId" : "clienteId") + "\":\"" + id + "\",\"nombre\":\"Nuevo\","
                + "\"precio\":12.34,\"activo\":false,\"existencia\":999,"
                + "\"correo\":\"privado@example.test\",\"telefono\":\"123\",\"direccion\":\"Privada\","
                + "\"fechaActualizacion\":\"2026-10-25T13:00:00Z\"}}";
        var mensaje = new Message(contenido.getBytes(StandardCharsets.UTF_8), new MessageProperties());
        if (producto) productos.alRecibir(mensaje);
        else clientes.alRecibir(mensaje);
    }
}
