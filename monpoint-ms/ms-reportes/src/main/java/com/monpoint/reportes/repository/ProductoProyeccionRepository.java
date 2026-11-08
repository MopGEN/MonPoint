package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.ProductoProyeccion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductoProyeccionRepository extends MongoRepository<ProductoProyeccion, String> {
}
