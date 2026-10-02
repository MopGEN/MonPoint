package com.monpoint.auth.dto.request;

import com.monpoint.auth.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales de login (H-010).
 * <p>
 * El constructor compacto normaliza el correo antes de que corra la validación, así
 * " Admin@MonPoint.com " llega al servicio como "admin@monpoint.com".
 */
public record LoginRequest(
        @NotBlank(message = "El correo no puede estar vacío")
        @Email(message = "Formato de correo inválido")
        String correo,

        @NotBlank(message = "La contraseña no puede estar vacía")
        String password
) {

    public LoginRequest {
        correo = correo == null ? null : Usuario.normalizarCorreo(correo);
    }

    /** Nunca exponer la contraseña si el request termina en un log. */
    @Override
    public String toString() {
        return "LoginRequest[correo=" + correo + ", password=" + (password == null ? null : "****") + "]";
    }
}
