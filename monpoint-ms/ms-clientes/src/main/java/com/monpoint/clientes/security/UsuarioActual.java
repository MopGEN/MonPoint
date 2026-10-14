package com.monpoint.clientes.security;

import com.monpoint.clientes.model.Rol;

/** Quién hace la petición, según las cabeceras que el Gateway sacó del token. */
public record UsuarioActual(String id, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
