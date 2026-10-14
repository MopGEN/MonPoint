package com.monpoint.clientes.repository;

import com.monpoint.clientes.model.Cliente;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClienteRepository extends MongoRepository<Cliente, String> {

    /** Búsqueda por nombre parcial, sin distinguir mayúsculas (H-040). */
    List<Cliente> findByNombreContainingIgnoreCase(String fragmento);
}
