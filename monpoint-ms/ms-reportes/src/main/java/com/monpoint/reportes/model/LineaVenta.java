package com.monpoint.reportes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Línea congelada de una venta: nombre y precio tal como estaban al venderse, aunque el catálogo cambie después.
 * Documento embebido en {@link VentaProyeccion}.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineaVenta {

    private String productoId;

    private String nombreProducto;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal precioUnitario;

    private int cantidad;

    public static LineaVenta de(String productoId, String nombreProducto, BigDecimal precioUnitario, int cantidad) {
        LineaVenta linea = new LineaVenta();
        linea.productoId = Objects.requireNonNull(productoId, "El productoId de la línea es obligatorio");
        linea.nombreProducto = Objects.requireNonNull(nombreProducto, "El nombre del producto es obligatorio");
        linea.precioUnitario = Objects.requireNonNull(precioUnitario, "El precio unitario es obligatorio");
        linea.cantidad = cantidad;
        return linea;
    }

    /** Importe de la línea; se calcula, no se guarda (el evento no trae subtotal). */
    public BigDecimal importe() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
