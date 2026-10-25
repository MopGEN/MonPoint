package com.monpoint.ventas.repository;

import com.monpoint.ventas.model.ClienteReplica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClienteReplicaRepository extends MongoRepository<ClienteReplica, String> {
}
