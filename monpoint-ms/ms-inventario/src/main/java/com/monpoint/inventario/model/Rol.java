package com.monpoint.inventario.model;

/**
 * Rol del usuario que hace la petición. Lo manda el Gateway en {@code X-User-Role}
 * (cada servicio declara el suyo: no hay librería compartida entre servicios).
 */
public enum Rol {
    ADMIN,
    VENDEDOR
}
