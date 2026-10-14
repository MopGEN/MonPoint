package com.monpoint.inventario.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Producto persistido en la colección {@code productos} (Doc 04 §5.1, H-020, H-021).
 * <p>
 * Campos exactamente como el Doc 04. El precio es {@code Decimal128} (RNF-08): sin
 * {@code targetType} Spring guardaría el {@link BigDecimal} como texto. La baja es lógica
 * ({@code activo = false}); el documento permanece (RF-2.2).
 * <p>
 * Sin setters: el estado solo cambia mediante métodos de dominio. La reserva de existencias NO pasa por aquí:
 * se hace con una actualización condicional atómica en {@code ReservaStockService}.
 */
@Document(collection = "productos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Producto {

    /**
     * ⚠️ Valor por omisión del umbral al crear (RF-2.6 / H-024: "configurable por producto, con valor por
     * omisión"). La documentación no fija el número; 5 es una propuesta a confirmar con el Product Owner.
     */
    public static final int UMBRAL_POR_OMISION = 5;

    @Id
    private String id;

    private String nombre;

    /** Opcional. */
    private String descripcion;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal precio;

    private int existencia;

    private int umbralStock;

    /** Doc 04 §11: índice para excluir inactivos del listado. */
    @Indexed
    private boolean activo;

    @CreatedDate
    private Instant fechaRegistro;

    /** Viaja en cada evento de producto, para que los consumidores descarten eventos desordenados. */
    @LastModifiedDate
    private Instant fechaActualizacion;

    public static Producto crear(String nombre, String descripcion, BigDecimal precio, int existencia,
                                 Integer umbralStock) {
        Producto producto = new Producto();
        producto.activo = true;
        producto.umbralStock = umbralStock != null ? umbralStock : UMBRAL_POR_OMISION;
        producto.asignarDatos(nombre, descripcion, precio, existencia);
        return producto;
    }

    /** {@code umbralStock} {@code null} conserva el umbral actual. */
    public void modificar(String nombre, String descripcion, BigDecimal precio, int existencia, Integer umbralStock) {
        asignarDatos(nombre, descripcion, precio, existencia);
        if (umbralStock != null) {
            this.umbralStock = umbralStock;
        }
    }

    public void darDeBaja() {
        this.activo = false;
    }

    private void asignarDatos(String nombre, String descripcion, BigDecimal precio, int existencia) {
        this.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio").trim();
        this.descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
        this.precio = Objects.requireNonNull(precio, "El precio es obligatorio");
        this.existencia = existencia;
    }
}
