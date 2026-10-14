package com.monpoint.clientes.repository;

import com.monpoint.clientes.model.Cliente;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integración contra MongoDB real (docker compose up -d mongodb): fechas automáticas, búsqueda parcial,
 * correo NO único e índice sobre nombre. Borra solo lo que crea.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class ClienteRepositoryTest {

    private static final String MARCA = "zz-prueba-repo-";

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private final List<String> creados = new ArrayList<>();

    @AfterEach
    void limpiar() {
        clienteRepository.deleteAllById(creados);
        creados.clear();
    }

    private Cliente guardar(String nombre, String correo) {
        Cliente guardado = clienteRepository.save(Cliente.crear(nombre, correo, "331", "Calle 1"));
        creados.add(guardado.getId());
        return guardado;
    }

    @Test
    void guardar_AsignaIdYFechasAutomaticas() {
        Cliente guardado = guardar(MARCA + "Ana", "ana@correo.com");

        assertNotNull(guardado.getId());
        assertNotNull(guardado.getFechaRegistro());
        assertNotNull(guardado.getFechaActualizacion());
    }

    @Test
    void buscarPorNombreParcial_NoDistingueMayusculas() {
        Cliente ana = guardar(MARCA + "Ana Pérez", null);
        guardar(MARCA + "Luis Gómez", null);

        List<Cliente> encontrados = clienteRepository.findByNombreContainingIgnoreCase((MARCA + "ANA").toUpperCase());

        assertEquals(1, encontrados.size());
        assertEquals(ana.getId(), encontrados.get(0).getId());
    }

    @Test
    void guardar_DosClientesConElMismoCorreo_EsValido() {
        guardar(MARCA + "Uno", "repetido@correo.com");
        guardar(MARCA + "Dos", "repetido@correo.com");

        assertEquals(2, clienteRepository.findByNombreContainingIgnoreCase(MARCA).stream()
                .filter(c -> "repetido@correo.com".equals(c.getCorreo())).count());
    }

    @Test
    void indice_ExisteUnIndiceSobreNombre() {
        boolean existe = mongoTemplate.indexOps(Cliente.class).getIndexInfo().stream()
                .anyMatch(info -> info.getIndexFields().stream().anyMatch(campo -> "nombre".equals(campo.getKey())));

        assertTrue(existe, "Doc 04 §11: debe existir un índice sobre clientes.nombre");
    }
}
