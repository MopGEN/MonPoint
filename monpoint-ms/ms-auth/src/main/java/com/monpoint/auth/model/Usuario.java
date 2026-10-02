package com.monpoint.auth.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Usuario del sistema persistido en la colección {@code usuarios} (H-011, H-072).
 * <p>
 * Sin setters: el estado solo cambia mediante métodos de dominio. El índice único
 * sobre {@code correo} es la última línea de defensa contra duplicados (409).
 */
@Document(collection = "usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field(name = "correo")
    private String correo;

    @Field(name = "password_hash")
    private String passwordHash;

    @Field(name = "nombre")
    private String nombre;

    @Field(name = "rol")
    private Rol rol;

    @Field(name = "activo")
    private boolean activo = true;

    @CreatedDate
    @Field(name = "creado_en")
    private Instant creadoEn;

    @LastModifiedDate
    @Field(name = "actualizado_en")
    private Instant actualizadoEn;

    public static Usuario create(String correo, String passwordHash, String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.correo = normalizarCorreo(correo);
        usuario.passwordHash = Objects.requireNonNull(passwordHash, "El hash de la contraseña es obligatorio");
        usuario.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio").trim();
        usuario.rol = Objects.requireNonNull(rol, "El rol es obligatorio");
        usuario.activo = true;
        return usuario;
    }

    /**
     * Forma canónica del correo: sin espacios y en minúsculas. Se aplica al registrar,
     * modificar y hacer login para que el índice único no distinga "Ana@x.com" de "ana@x.com".
     * {@link Locale#ROOT} evita conversiones dependientes del idioma del sistema.
     */
    public static String normalizarCorreo(String correo) {
        return Objects.requireNonNull(correo, "El correo es obligatorio").trim().toLowerCase(Locale.ROOT);
    }

    public void cambiarPassword(String nuevoPasswordHash) {
        this.passwordHash = Objects.requireNonNull(nuevoPasswordHash, "El hash de la contraseña es obligatorio");
    }

    public void actualizarDatos(String nombre, String correo) {
        this.nombre = Objects.requireNonNull(nombre, "El nombre es obligatorio").trim();
        this.correo = normalizarCorreo(correo);
    }

    /** Quién puede cambiar el rol de quién (H-013) lo decide el servicio, no la entidad. */
    public void cambiarRol(Rol nuevoRol) {
        this.rol = Objects.requireNonNull(nuevoRol, "El rol es obligatorio");
    }

    public void desactivar() {
        this.activo = false;
    }

    public void activar() {
        this.activo = true;
    }
}
