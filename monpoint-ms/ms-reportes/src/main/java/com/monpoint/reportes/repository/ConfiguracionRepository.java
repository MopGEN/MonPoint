package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.Configuracion;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConfiguracionRepository extends MongoRepository<Configuracion, String> {
}
