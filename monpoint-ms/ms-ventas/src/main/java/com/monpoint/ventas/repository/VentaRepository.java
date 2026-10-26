package com.monpoint.ventas.repository;

import com.monpoint.ventas.model.Venta;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VentaRepository extends MongoRepository<Venta, String> { }
