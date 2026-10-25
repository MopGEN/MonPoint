package com.monpoint.ventas.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/** Réplica del catálogo de ms-inventario; la existencia pertenece exclusivamente a ese servicio. */
@Document(collection = "productos_replica")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductoReplica {

    /** Conserva el identificador del producto en ms-inventario. */
    @Id
    private String id;

    private String nombre;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal precio;

    private boolean activo;

    /** Fecha del evento de origen; no se actualiza mediante auditoría de persistencia. */
    private Instant fechaActualizacion;

    public static ProductoReplica crear(String id, String nombre, BigDecimal precio, boolean activo,
                                       Instant fechaActualizacion) {
        ProductoReplica producto = new ProductoReplica();
        producto.id = id;
        producto.actualizarDesde(nombre, precio, activo, fechaActualizacion);
        return producto;
    }

    /** El consumidor decide previamente si corresponde aplicar el evento. */
    public void actualizarDesde(String nombre, BigDecimal precio, boolean activo, Instant fechaActualizacion) {
        this.nombre = nombre;
        this.precio = precio;
        this.activo = activo;
        this.fechaActualizacion = fechaActualizacion;
    }
}
