package com.monpoint.ventas.repository;

import com.monpoint.ventas.model.ProductoReplica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductoReplicaRepository extends MongoRepository<ProductoReplica, String> {
}
