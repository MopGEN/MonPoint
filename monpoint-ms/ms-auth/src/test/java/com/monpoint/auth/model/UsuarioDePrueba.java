package com.monpoint.auth.model;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Crea usuarios como si vinieran de MongoDB. {@code Usuario} no tiene setter de id (lo asigna
 * Mongo al guardar), así que las pruebas lo fijan por reflexión.
 */
public final class UsuarioDePrueba {

    public static final String HASH = "hash-bcrypt";

    private UsuarioDePrueba() {
    }

    public static Usuario conId(String id, String correo, Rol rol) {
        Usuario usuario = Usuario.create(correo, HASH, "Nombre " + rol, rol);
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    public static Usuario inactivo(String id, String correo, Rol rol) {
        Usuario usuario = conId(id, correo, rol);
        usuario.desactivar();
        return usuario;
    }
}
