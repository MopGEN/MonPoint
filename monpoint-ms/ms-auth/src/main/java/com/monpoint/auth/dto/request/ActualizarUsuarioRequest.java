package com.monpoint.auth.dto.request;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Modificación de usuario vía PUT (H-011, H-013): nombre y correo siempre se envían.
 *
 * @param password opcional: nulo o vacío conserva la contraseña actual
 * @param rol      opcional: nulo conserva el rol actual; quién puede cambiarlo lo decide el servicio
 */
public record ActualizarUsuarioRequest(
        @NotBlank(message = "El nombre no puede estar vacío")
        String nombre,

        @NotBlank(message = "El correo no puede estar vacío")
        @Email(message = "Formato de correo inválido")
        String correo,

        String password,

        Rol rol
) {

    public ActualizarUsuarioRequest {
        correo = correo == null ? null : Usuario.normalizarCorreo(correo);
    }

    public boolean cambiaPassword() {
        return password != null && !password.isEmpty();
    }

    @Override
    public String toString() {
        return "ActualizarUsuarioRequest[nombre=" + nombre + ", correo=" + correo
                + ", password=" + (password == null ? null : "****") + ", rol=" + rol + "]";
    }
}
