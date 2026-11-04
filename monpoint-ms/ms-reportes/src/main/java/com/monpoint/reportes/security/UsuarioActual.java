package com.monpoint.reportes.security;

import com.monpoint.reportes.model.Rol;

public record UsuarioActual(String id, Rol rol) {

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
//record: es una clase inmutable que solo guarda datos. Java genera el constructor y los métodos id() y rol().
//esAdmin(): permite que las reglas se lean claro en los servicios.