package com.monpoint.ventas.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Document(collection = "ventas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venta {
    @Id
    private String id;
    @Indexed(unique = true)
    private long folio;
    @Field(targetType = FieldType.OBJECT_ID)
    private String clienteId;
    private String clienteNombre;
    private EstadoVenta estado;
    private String motivoCancelacion;
    /**
     * Producto que provocó el rechazo, solo en CANCELADA (actividad 5.5, paso 9).
     * <p>
     * {@code motivoCancelacion} ya nombra el producto dentro de la frase, pero como texto. La caja
     * necesita señalar la línea exacta del carrito, y analizar la frase para extraer el nombre sería
     * frágil: bastaría con reescribir el mensaje para romperlo. Aquí viaja el identificador.
     * <p>
     * Las ventas canceladas antes de esta actividad no tienen el campo y se leen como null; el cliente
     * debe tolerarlo.
     */
    @Field(targetType = FieldType.OBJECT_ID)
    private String productoIdRechazado;
    private List<LineaVenta> lineas;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal total;
    private Instant fechaCreacion;
    private Instant fechaResolucion;

    public static Venta pendiente(String clienteId, String clienteNombre,
                                  List<LineaVenta> lineas, Instant fechaCreacion) {
        if (lineas == null || lineas.isEmpty()) {
            throw new IllegalArgumentException("La venta requiere líneas");
        }
        Venta venta = new Venta();
        venta.clienteId = clienteId;
        venta.clienteNombre = clienteNombre;
        venta.estado = EstadoVenta.PENDIENTE;
        venta.lineas = List.copyOf(lineas);
        venta.total = venta.lineas.stream().map(LineaVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        venta.fechaCreacion = Objects.requireNonNull(fechaCreacion);
        return venta;
    }

    public void asignarFolio(long folio) {
        if (this.folio != 0 || folio <= 0) throw new IllegalStateException("El folio debe asignarse una sola vez");
        this.folio = folio;
    }

    public void confirmar(Instant fecha) {
        exigirPendiente();
        this.fechaResolucion = Objects.requireNonNull(fecha, "La fecha de resolución es obligatoria");
        this.motivoCancelacion = null;
        this.productoIdRechazado = null;
        this.estado = EstadoVenta.CONFIRMADA;
    }

    /**
     * @param productoIdRechazado producto que provocó el rechazo. Es obligatorio y no un añadido
     *     opcional: toda cancelación nace de un StockRechazado, que siempre identifica un producto.
     *     Exigirlo aquí impide que una ruta futura cancele sin dejar ese rastro, que es justo lo que
     *     la pantalla de caja necesita para señalar la línea culpable.
     */
    public void cancelar(String motivo, String productoIdRechazado, Instant fecha) {
        exigirPendiente();
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("El motivo es obligatorio");
        if (productoIdRechazado == null || productoIdRechazado.isBlank()) {
            throw new IllegalArgumentException("El producto que provocó el rechazo es obligatorio");
        }
        this.fechaResolucion = Objects.requireNonNull(fecha, "La fecha de resolución es obligatoria");
        this.motivoCancelacion = motivo;
        this.productoIdRechazado = productoIdRechazado;
        this.estado = EstadoVenta.CANCELADA;
    }

    private void exigirPendiente() {
        if (estado != EstadoVenta.PENDIENTE) throw new IllegalStateException("La venta ya está resuelta");
    }

    /** MongoDB puede materializar un ArrayList; tampoco se expone mutable después de leerlo. */
    public List<LineaVenta> getLineas() {
        return List.copyOf(lineas);
    }
}
