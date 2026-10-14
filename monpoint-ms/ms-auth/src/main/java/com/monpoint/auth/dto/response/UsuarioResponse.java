package com.monpoint.auth.dto.response;

import com.monpoint.auth.model.Usuario;

/**
 * Vista pública de un usuario (H-011): nunca incluye el hash de la contraseña.
 */
public record UsuarioResponse(
        String id,
        String nombre,
        String correo,
        String rol,
        boolean activo
) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getRol().name(), usuario.isActivo());
    }
}
