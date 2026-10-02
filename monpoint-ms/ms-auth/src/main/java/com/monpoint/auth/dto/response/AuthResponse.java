package com.monpoint.auth.dto.response;

import com.monpoint.auth.model.Usuario;

/**
 * Resultado de un login exitoso (H-010).
 *
 * @param tipo     esquema del header Authorization: siempre "Bearer"
 * @param rol      rol singular: "ADMIN" o "VENDEDOR"
 * @param expiraEn vigencia del token en milisegundos ({@code jwt.expiration})
 */
public record AuthResponse(
        String token,
        String tipo,
        String correo,
        String nombre,
        String rol,
        long expiraEn
) {

    private static final String TIPO_BEARER = "Bearer";

    public static AuthResponse from(Usuario usuario, String token, long expiraEn) {
        return new AuthResponse(token, TIPO_BEARER, usuario.getCorreo(), usuario.getNombre(),
                usuario.getRol().name(), expiraEn);
    }
}
