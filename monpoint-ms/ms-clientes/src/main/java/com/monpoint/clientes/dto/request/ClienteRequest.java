package com.monpoint.clientes.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Alta y modificación de un cliente (H-040, H-042). Si varios campos son inválidos, la respuesta 400
 * los lista todos (ver GlobalExceptionHandler).
 * <p>
 * El correo es opcional, pero si viene debe tener formato válido (RF-4.3). No se exige que sea único.
 */
public record ClienteRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        @Email(message = "El correo no tiene un formato válido") String correo,
        String telefono,
        String direccion) {
}
