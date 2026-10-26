package com.monpoint.ventas.repository;

import com.monpoint.ventas.model.Contador;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContadorRepository extends MongoRepository<Contador, String> { }
