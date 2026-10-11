package com.monpoint.gateway.security;

/**
 * Identidad leída de un token válido. El Gateway la propaga a los servicios como {@code X-User-Id} y
 * {@code X-User-Role}.
 *
 * @param userId id del usuario en MongoDB (claim {@code userId})
 * @param correo correo normalizado (claim {@code sub})
 * @param rol    {@code ADMIN} o {@code VENDEDOR} (claim {@code rol})
 */
public record IdentidadUsuario(String userId, String correo, String rol) {
}
