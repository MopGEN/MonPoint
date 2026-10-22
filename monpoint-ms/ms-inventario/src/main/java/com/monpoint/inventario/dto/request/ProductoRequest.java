package com.monpoint.inventario.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Alta y modificación de un producto (H-020). Precio o existencia negativos devuelven 400 y la respuesta
 * lista todos los campos inválidos.
 */
public record ProductoRequest(
        @NotBlank(message = "El nombre es obligatorio") String nombre,
        String descripcion,
        @NotNull(message = "El precio es obligatorio")
        @PositiveOrZero(message = "El precio no puede ser negativo") BigDecimal precio,
        @NotNull(message = "La existencia es obligatoria")
        @PositiveOrZero(message = "La existencia no puede ser negativa") Integer existencia,
        @PositiveOrZero(message = "El umbral no puede ser negativo") Integer umbralStock) {
}
