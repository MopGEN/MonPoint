package com.monpoint.auth.security;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;

import java.security.Principal;

/**
 * Identidad del usuario autenticado, reconstruida del JWT en cada petición sin consultar MongoDB.
 * <p>
 * Es el {@code principal} del {@code Authentication}. Implementa {@link Principal} para que
 * {@code authentication.getName()} devuelva el correo en vez del {@code toString()} del record.
 */
public record UserPrincipal(String id, String correo, Rol rol) implements Principal {

    public static UserPrincipal from(Usuario usuario) {
        return new UserPrincipal(usuario.getId(), usuario.getCorreo(), usuario.getRol());
    }

    @Override
    public String getName() {
        return correo;
    }
}
