package com.monpoint.reportes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Venta cerrada, alimentada solo por VentaConfirmada / VentaCancelada (Doc 04: {@code ventas_proy}, H-055).
 * <p>
 * El {@code _id} es el MISMO id de la venta en ms-ventas: así el ticket se busca por ese id y una segunda
 * entrega reemplaza el documento en lugar de duplicarlo. {@code fecha} es la de resolución (confirmación o
 * cancelación): es la que cuenta para el historial y las gráficas, que leen el índice {@code (fecha, estado)}.
 * <p>
 * Las canceladas también se guardan: así el ticket de una venta cancelada responde 409 y no 404.
 */
@Document(collection = "ventas_proy")
@CompoundIndex(name = "fecha_estado", def = "{'fecha': 1, 'estado': 1}")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VentaProyeccion {

    @Id
    private String id;

    private long folio;

    private String clienteNombre;

    private EstadoVenta estado;

    private List<LineaVenta> lineas;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal total;

    private Instant fecha;

    public static VentaProyeccion de(String id, long folio, String clienteNombre, EstadoVenta estado,
                                     List<LineaVenta> lineas, BigDecimal total, Instant fecha) {
        VentaProyeccion venta = new VentaProyeccion();
        venta.id = Objects.requireNonNull(id, "El id de la venta es obligatorio");
        venta.folio = folio;
        venta.clienteNombre = clienteNombre;
        venta.estado = Objects.requireNonNull(estado, "El estado es obligatorio");
        venta.lineas = List.copyOf(Objects.requireNonNull(lineas, "Las líneas son obligatorias"));
        venta.total = Objects.requireNonNull(total, "El total es obligatorio");
        venta.fecha = Objects.requireNonNull(fecha, "La fecha de resolución es obligatoria");
        return venta;
    }
}
