package com.monpoint.reportes.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Datos de la empresa para el encabezado de los documentos (H-050, Doc 04: colección {@code configuracion}).
 * <p>
 * Hay un solo documento y su {@code _id} es fijo ({@value #ID_UNICO}): "registrar" cuando ya existe
 * actualiza el mismo documento en lugar de crear otro. Sin setters: el estado cambia con {@link #actualizar}.
 */
@Document(collection = "configuracion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Configuracion {

    public static final String ID_UNICO = "config";

    @Id
    private String id;

    private String nombreEmpresa;

    private String rfc;

    private String direccion;

    private String telefono;

    /** Se asigna aquí y no con auditoría: con un _id fijo, Spring Data no distingue alta de modificación. */
    private Instant fechaActualizacion;

    public static Configuracion nueva(String nombreEmpresa, String rfc, String direccion, String telefono,
                                      Instant ahora) {
        Configuracion configuracion = new Configuracion();
        configuracion.id = ID_UNICO;
        configuracion.actualizar(nombreEmpresa, rfc, direccion, telefono, ahora);
        return configuracion;
    }

    public void actualizar(String nombreEmpresa, String rfc, String direccion, String telefono, Instant ahora) {
        this.nombreEmpresa = Objects.requireNonNull(nombreEmpresa, "El nombre de la empresa es obligatorio").trim();
        this.rfc = Objects.requireNonNull(rfc, "El RFC es obligatorio").trim().toUpperCase();
        this.direccion = limpiar(direccion);
        this.telefono = limpiar(telefono);
        this.fechaActualizacion = Objects.requireNonNull(ahora, "La fecha es obligatoria");
    }

    /** Un texto vacío o de puros espacios se guarda como ausente. */
    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
