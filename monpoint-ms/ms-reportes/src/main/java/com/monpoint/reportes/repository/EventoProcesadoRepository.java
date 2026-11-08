package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.EventoProcesado;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventoProcesadoRepository extends MongoRepository<EventoProcesado, String> {

    boolean existsByEventoId(String eventoId);
}
