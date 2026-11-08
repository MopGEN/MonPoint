package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.ContraparteProyeccion;
import com.monpoint.reportes.model.TipoContraparte;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ContraparteProyeccionRepository extends MongoRepository<ContraparteProyeccion, String> {

    /** Usa el índice sobre {@code tipo}. El orden se aplica en el servicio. */
    List<ContraparteProyeccion> findByTipo(TipoContraparte tipo);
}
