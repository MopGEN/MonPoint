package com.monpoint.clientes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Cliente persistido en la colección {@code clientes} (Doc 04 §7, H-040).
 * <p>
 * Campos exactamente como el Doc 04: {@code _id}, {@code nombre}, {@code correo}, {@code telefono},
 * {@code direccion}, {@code fechaRegistro} y {@code fechaActualizacion}. El correo NO es único.
 * El índice sobre {@code nombre} sirve a la búsqueda por nombre parcial.
 * <p>
 * Sin setters: el estado solo cambia mediante métodos de dominio.
 */
@Document(collection = "clientes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente {

    @Id
    private String id;

    @Indexed
    private String nombre;

    private String correo;

    private String telefono;

    private String direccion;

    /** RF-4.5: la fecha de alta es automática. */
    @CreatedDate
    private Instant fechaRegistro;

    @LastModifiedDate
    private Instant fechaActualizacion;

    public static Cliente crear(String nombre, String correo, String telefono, String direccion) {
        Cliente cliente = new Cliente();
        cliente.asignarDatos(nombre, correo, telefono, direccion);
        return cliente;
    }

    public void actualizarDatos(String nombre, String correo, String telefono, String direccion) {
        asignarDatos(nombre, correo, telefono, direccion);
    }

    private void asignarDatos(String nombre, String correo, String telefono, String direccion) {
        this.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio").trim();
        this.correo = limpiar(correo);
        this.telefono = limpiar(telefono);
        this.direccion = limpiar(direccion);
    }

    /** Quita espacios sobrantes; un texto vacío se guarda como {@code null}. */
    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
