package com.monpoint.notificaciones.security;

import com.monpoint.notificaciones.model.Rol;

public record UsuarioActual(String id, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}