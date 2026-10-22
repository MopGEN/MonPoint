package com.monpoint.inventario.repository;

import com.monpoint.inventario.model.EventoProcesado;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoProcesadoRepository extends MongoRepository<EventoProcesado, String> {

    boolean existsByEventoId(String eventoId);
}
