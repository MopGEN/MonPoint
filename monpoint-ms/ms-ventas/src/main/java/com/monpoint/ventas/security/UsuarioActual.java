package com.monpoint.ventas.security;

/** Identidad de las cabeceras que el Gateway obtiene del JWT y sobrescribe. */
public record UsuarioActual(String id, String rol) { }
