package com.monpoint.inventario.repository;

import com.monpoint.inventario.model.Producto;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integración contra MongoDB real (docker compose up -d mongodb): Decimal128 (RNF-08), listado sin inactivos
 * (H-021) e índices del Doc 04 §11. Borra solo lo que crea.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class ProductoRepositoryTest {

    private static final String MARCA = "zz-prueba-repo-";

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<String> creados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        productoRepository.deleteAllById(creados);
        creados.clear();
    }

    private Producto guardar(String nombre, boolean activo) {
        Producto producto = Producto.crear(MARCA + nombre, null, new BigDecimal("45.50"), 10, null);
        if (!activo) {
            producto.darDeBaja();
        }
        Producto guardado = productoRepository.save(producto);
        creados.add(guardado.getId());
        return guardado;
    }

    @Test
    void guardar_PersisteElPrecioComoDecimal128YNoComoTexto() {
        Producto guardado = guardar("decimal", true);

        Document crudo = mongoTemplate.getCollection("productos")
                .find(new Document("_id", new ObjectId(guardado.getId()))).first();

        assertNotNull(crudo);
        assertTrue(crudo.get("precio") instanceof Decimal128, "RNF-08: precio debe ser Decimal128");
        assertEquals(new BigDecimal("45.50"), productoRepository.findById(guardado.getId()).orElseThrow().getPrecio());
    }

    @Test
    void guardar_AsignaFechasAutomaticasYUmbralPorOmision() {
        Producto guardado = guardar("fechas", true);

        assertNotNull(guardado.getFechaRegistro());
        assertNotNull(guardado.getFechaActualizacion());
        assertEquals(Producto.UMBRAL_POR_OMISION, guardado.getUmbralStock());
    }

    @Test
    void findByActivoTrue_ExcluyeLosProductosDadosDeBaja() {
        Producto activo = guardar("activo", true);
        Producto inactivo = guardar("inactivo", false);

        List<String> ids = productoRepository.findByActivoTrue().stream().map(Producto::getId).toList();

        assertTrue(ids.contains(activo.getId()));
        assertTrue(!ids.contains(inactivo.getId()), "el inactivo no debe salir en el listado");
        assertTrue(productoRepository.findById(inactivo.getId()).isPresent(), "pero el documento permanece");
    }

    @Test
    void indices_ExisteElIndiceDeActivoYElUnicoDeEventoId() {
        assertTrue(mongoTemplate.indexOps(Producto.class).getIndexInfo().stream()
                .anyMatch(i -> i.getIndexFields().stream().anyMatch(c -> "activo".equals(c.getKey()))));
        assertTrue(mongoTemplate.indexOps("eventos_procesados").getIndexInfo().stream()
                .anyMatch(i -> i.isUnique() && i.getIndexFields().stream().anyMatch(c -> "eventoId".equals(c.getKey()))));
    }
}
