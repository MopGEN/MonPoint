package com.monpoint.reportes.listener;

import com.monpoint.reportes.model.EventoProcesado;
import com.monpoint.reportes.model.VentaProyeccion;
import com.monpoint.reportes.repository.EventoProcesadoRepository;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

/**
 * Corre contra el MongoDB local (replica set rs0) que usa el servicio. Cada prueba usa ids nuevos y al final
 * borra SOLO sus documentos, para no tocar datos de desarrollo. Los listeners de RabbitMQ no arrancan.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class ProyeccionesIntegracionTest {

    private final String ventaId = new ObjectId().toHexString();
    private final String eventoId = "zz-prueba-" + new ObjectId().toHexString();

    @Autowired VentaCierreListener listener;
    @Autowired MongoTemplate mongo;
    @MockitoSpyBean EventoProcesadoRepository eventos;

    @AfterEach
    void limpiarSoloDocumentosDeEstaPrueba() {
        mongo.remove(Query.query(Criteria.where("_id").is(ventaId)), VentaProyeccion.class);
        mongo.remove(Query.query(Criteria.where("eventoId").is(eventoId)), EventoProcesado.class);
    }

    /** Spring Data convierte a ObjectId un id String con formato de ObjectId: así queda el _id del Doc 04. */
    @Test
    void ventaSeGuardaConImportesEnDecimal128YFechaComoDate() {
        listener.alRecibir(mensaje("VentaConfirmada"));

        Document venta = ventaGuardada();
        assertThat(venta).isNotNull();
        assertThat(venta.get("total")).isInstanceOf(Decimal128.class);
        assertThat(((Decimal128) venta.get("total")).bigDecimalValue()).isEqualByComparingTo("70.50");
        assertThat(venta.get("folio")).isInstanceOf(Long.class);
        assertThat(venta.getString("estado")).isEqualTo("CONFIRMADA");
        assertThat(venta.getDate("fecha").toInstant()).isEqualTo(Instant.parse("2026-11-08T05:30:00Z"));

        List<Document> lineas = venta.getList("lineas", Document.class);
        assertThat(lineas).hasSize(1);
        assertThat(lineas.getFirst().get("precioUnitario")).isInstanceOf(Decimal128.class);
        assertThat(lineas.getFirst().getString("nombreProducto")).isEqualTo("Café");
    }

    @Test
    void segundaEntregaDelMismoEventoSeIgnora() {
        listener.alRecibir(mensaje("VentaConfirmada"));
        listener.alRecibir(mensaje("VentaConfirmada"));

        assertThat(mongo.count(Query.query(Criteria.where("eventoId").is(eventoId)), EventoProcesado.class))
                .isEqualTo(1);
        assertThat(mongo.getCollection("ventas_proy").countDocuments(porId())).isEqualTo(1);
    }

    @Test
    void choqueEnElIndiceUnicoRevierteLaProyeccion() {
        eventos.save(EventoProcesado.de(eventoId, "Original", Instant.now()));
        // Simula que otra entrega registró el id después de la comprobación: el índice único debe
        // rechazar el registro y la transacción debe deshacer también la venta.
        doReturn(false).when(eventos).existsByEventoId(eventoId);

        assertThatThrownBy(() -> listener.alRecibir(mensaje("VentaConfirmada")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(ventaGuardada()).isNull();
    }

    @Test
    void indicesDelModeloExisten() {
        List<String> ventas = nombresDeIndices("ventas_proy");
        assertThat(ventas).contains("fecha_estado");

        Document eventoIdUnico = mongo.getCollection("eventos_procesados").listIndexes()
                .into(new ArrayList<>()).stream()
                .filter(indice -> indice.get("key", Document.class).containsKey("eventoId"))
                .findFirst()
                .orElseThrow();
        assertThat(eventoIdUnico.getBoolean("unique")).isTrue();
        assertThat(nombresDeIndices("contrapartes_proy")).anyMatch(nombre -> nombre.startsWith("tipo"));
    }

    private Document ventaGuardada() {
        return mongo.getCollection("ventas_proy").find(porId()).first();
    }

    private Document porId() {
        return new Document("_id", new ObjectId(ventaId));
    }

    private List<String> nombresDeIndices(String coleccion) {
        return mongo.getCollection(coleccion).listIndexes().map(indice -> indice.getString("name"))
                .into(new ArrayList<>());
    }

    private Message mensaje(String tipo) {
        String json = """
                {"eventoId":"%s","tipo":"%s","version":"1","fecha":"2026-11-08T05:30:01Z","origen":"ms-ventas",
                 "datos":{"ventaId":"%s","folio":9000000001,"clienteNombre":null,"total":70.50,
                  "fechaCreacion":"2026-11-08T05:29:58Z","fechaResolucion":"2026-11-08T05:30:00Z",
                  "lineas":[{"productoId":"p1","nombreProducto":"Café","cantidad":2,"precioUnitario":35.25}]}}
                """.formatted(eventoId, tipo, ventaId);
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
