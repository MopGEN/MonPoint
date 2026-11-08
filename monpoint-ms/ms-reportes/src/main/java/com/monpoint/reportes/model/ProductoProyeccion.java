package com.monpoint.reportes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Copia del catálogo para el listado en PDF (Doc 04: {@code productos_proy}, H-056). Sin existencias:
 * la autoridad del stock es ms-inventario y sus eventos de producto no la traen.
 */
@Document(collection = "productos_proy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductoProyeccion {

    /** El mismo id del producto en ms-inventario. */
    @Id
    private String id;

    private String nombre;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal precio;

    private boolean activo;

    /** Fecha del evento de origen; sirve para descartar eventos que llegan desordenados. */
    private Instant fechaActualizacion;

    public static ProductoProyeccion crear(String id, String nombre, BigDecimal precio, boolean activo,
                                          Instant fechaActualizacion) {
        ProductoProyeccion producto = new ProductoProyeccion();
        producto.id = id;
        producto.actualizarDesde(nombre, precio, activo, fechaActualizacion);
        return producto;
    }

    /** El listener decide antes si corresponde aplicar el evento. */
    public void actualizarDesde(String nombre, BigDecimal precio, boolean activo, Instant fechaActualizacion) {
        this.nombre = nombre;
        this.precio = precio;
        this.activo = activo;
        this.fechaActualizacion = fechaActualizacion;
    }
}
