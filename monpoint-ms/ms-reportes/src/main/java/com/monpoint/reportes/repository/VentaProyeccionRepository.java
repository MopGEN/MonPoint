package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.VentaProyeccion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VentaProyeccionRepository extends MongoRepository<VentaProyeccion, String> {
}
