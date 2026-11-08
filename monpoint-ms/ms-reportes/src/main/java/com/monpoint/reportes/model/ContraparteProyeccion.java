package com.monpoint.reportes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Directorio para el listado en PDF (Doc 04: {@code contrapartes_proy}, H-056). Hoy solo llegan clientes
 * ({@code tipo = CLIENTE}); los campos de proveedor ({@code ruc}, {@code razonSocial}) no se modelan porque
 * ningún servicio los publica.
 * <p>
 * Limitación: ms-clientes no publica bajas, así que un cliente eliminado allá permanece aquí.
 */
@Document(collection = "contrapartes_proy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContraparteProyeccion {

    /** El mismo id del cliente en ms-clientes. */
    @Id
    private String id;

    @Indexed
    private TipoContraparte tipo;

    private String nombre;

    private String correo;

    private String telefono;

    private String direccion;

    /** Fecha del evento de origen; sirve para descartar eventos que llegan desordenados. */
    private Instant fechaActualizacion;

    public static ContraparteProyeccion cliente(String id, String nombre, String correo, String telefono,
                                                String direccion, Instant fechaActualizacion) {
        ContraparteProyeccion cliente = new ContraparteProyeccion();
        cliente.id = id;
        cliente.tipo = TipoContraparte.CLIENTE;
        cliente.actualizarDesde(nombre, correo, telefono, direccion, fechaActualizacion);
        return cliente;
    }

    /** El listener decide antes si corresponde aplicar el evento. */
    public void actualizarDesde(String nombre, String correo, String telefono, String direccion,
                                Instant fechaActualizacion) {
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.direccion = direccion;
        this.fechaActualizacion = fechaActualizacion;
    }
}
