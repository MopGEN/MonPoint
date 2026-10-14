package com.monpoint.auth.repository;

import com.monpoint.auth.model.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    /**
     * ¿Otro usuario (distinto de {@code id}) ya usa este correo? Evita el falso 409
     * cuando un usuario guarda su perfil conservando su propio correo.
     */
    boolean existsByCorreoAndIdNot(String correo, String id);

    List<Usuario> findByActivoTrue();
}
