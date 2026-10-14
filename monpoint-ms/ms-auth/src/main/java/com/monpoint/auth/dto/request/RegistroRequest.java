package com.monpoint.auth.dto.request;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Alta de usuario por un ADMIN (H-011, H-012).
 * <p>
 * Aquí solo se exige que la contraseña venga; su política completa la aplica
 * {@code PasswordValidator} en el servicio para reportar cada regla incumplida.
 * Un rol fuera de ADMIN/VENDEDOR (p. ej. "OTRO") falla al deserializar el JSON.
 */
public record RegistroRequest(
        @NotBlank(message = "El nombre no puede estar vacío")
        String nombre,

        @NotBlank(message = "El correo no puede estar vacío")
        @Email(message = "Formato de correo inválido")
        String correo,

        @NotBlank(message = "La contraseña no puede estar vacía")
        String password,

        @NotNull(message = "El rol es obligatorio (ADMIN o VENDEDOR)")
        Rol rol
) {

    public RegistroRequest {
        correo = correo == null ? null : Usuario.normalizarCorreo(correo);
    }

    @Override
    public String toString() {
        return "RegistroRequest[nombre=" + nombre + ", correo=" + correo
                + ", password=" + (password == null ? null : "****") + ", rol=" + rol + "]";
    }
}
