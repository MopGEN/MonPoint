package com.monpoint.reportes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta o modificación de los datos de la empresa (H-050). Si varios campos son inválidos, la respuesta 400
 * los lista todos (ver GlobalExceptionHandler).
 * <p>
 * RFC mexicano: 3 letras (persona moral) o 4 (persona física), 6 dígitos de fecha y 3 de homoclave.
 * Se acepta en minúsculas y se guarda en mayúsculas.
 */
public record ConfiguracionRequest(
        @NotBlank(message = "El nombre de la empresa es obligatorio")
        @Size(max = 120, message = "El nombre de la empresa admite hasta 120 caracteres")
        String nombreEmpresa,

        @NotBlank(message = "El RFC es obligatorio")
        @Pattern(regexp = "\\s*[A-Za-zÑñ&]{3,4}\\d{6}[A-Za-z0-9]{3}\\s*", message = "El RFC no tiene un formato válido")
        String rfc,

        @Size(max = 200, message = "La dirección admite hasta 200 caracteres")
        String direccion,

        @Size(max = 30, message = "El teléfono admite hasta 30 caracteres")
        String telefono) {
}
