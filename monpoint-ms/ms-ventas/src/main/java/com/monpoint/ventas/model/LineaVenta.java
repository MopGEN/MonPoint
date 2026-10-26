package com.monpoint.ventas.model;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.math.BigDecimal;
import java.util.Objects;

/** Valores históricos inmutables. El subtotal se calcula, no se persiste. */
public record LineaVenta(@Field(targetType = FieldType.OBJECT_ID) String productoId,
                         String nombreProducto,
                         @Field(targetType = FieldType.DECIMAL128) BigDecimal precioUnitario,
                         int cantidad) {
    public LineaVenta {
        Objects.requireNonNull(productoId);
        Objects.requireNonNull(nombreProducto);
        Objects.requireNonNull(precioUnitario);
        if (cantidad <= 0 || precioUnitario.signum() < 0) {
            throw new IllegalArgumentException("Cantidad o precio inválidos");
        }
    }

    public BigDecimal subtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
