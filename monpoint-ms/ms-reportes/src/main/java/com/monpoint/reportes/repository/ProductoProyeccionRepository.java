package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.ProductoProyeccion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductoProyeccionRepository extends MongoRepository<ProductoProyeccion, String> {

    /** Para el listado en PDF: los dados de baja no aparecen (H-053). El orden se aplica en el servicio. */
    List<ProductoProyeccion> findByActivoTrue();
}
