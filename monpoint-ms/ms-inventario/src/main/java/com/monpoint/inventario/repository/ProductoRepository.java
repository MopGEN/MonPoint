package com.monpoint.inventario.repository;

import com.monpoint.inventario.model.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoRepository extends MongoRepository<Producto, String> {

    /** Listado general: excluye los productos dados de baja (H-021). */
    List<Producto> findByActivoTrue();
}
