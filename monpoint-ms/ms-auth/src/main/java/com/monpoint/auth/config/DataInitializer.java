package com.monpoint.auth.config;

import com.monpoint.auth.exception.PasswordValidationException;
import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.repository.UsuarioRepository;
import com.monpoint.auth.validator.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Semilla nominal optativa: solo añade correos ausentes, nunca modifica cuentas existentes. */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {
    private record Cuenta(String nombre, String correo, Rol rol, String variable) {}

    private static final List<Cuenta> CUENTAS = List.of(
            new Cuenta("Víctor Ortiz", "victor.ortiz@autotachi.mx", Rol.ADMIN, "AUTOTACHI_VICTOR_PASSWORD"),
            new Cuenta("Alejandro Bermúdez", "alejandro.bermudez@autotachi.mx", Rol.ADMIN, "AUTOTACHI_ALEJANDRO_PASSWORD"),
            new Cuenta("Hugo Trejo", "hugo.trejo@autotachi.mx", Rol.ADMIN, "AUTOTACHI_HUGO_PASSWORD"),
            new Cuenta("Vendedor", "vendedor@autotachi.mx", Rol.VENDEDOR, "AUTOTACHI_VENDEDOR_PASSWORD"));

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final PasswordValidator validator;
    private final Environment env;

    public DataInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
                           PasswordValidator validator, Environment env) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.validator = validator;
        this.env = env;
    }

    @Override
    public void run(String... args) {
        if (!env.getProperty("SEMILLA_NOMINAL_HABILITADA", Boolean.class, false)) {
            if (usuarios.count() == 0) {
                throw new IllegalStateException("Base de usuarios vacía: configurar las cuatro AUTOTACHI_*_PASSWORD "
                        + "y SEMILLA_NOMINAL_HABILITADA=true. No hay contraseña por omisión.");
            }
            log.info("Semilla nominal deshabilitada: se conservan los usuarios existentes");
            return;
        }
        // Incluye inactivos: una baja o un cambio de rol/clave no se deshace al reiniciar.
        List<Cuenta> faltantes = CUENTAS.stream().filter(c -> !usuarios.existsByCorreo(c.correo())).toList();
        // Validar todo el lote antes de guardar la primera cuenta. No recortar contraseñas.
        for (Cuenta cuenta : faltantes) {
            try {
                validator.validar(env.getProperty(cuenta.variable()));
            } catch (PasswordValidationException ex) {
                throw new IllegalStateException("Configurar " + cuenta.variable()
                        + ": debe cumplir la política de contraseñas de ms-auth.");
            }
        }
        for (Cuenta cuenta : faltantes) {
            Usuario usuario = Usuario.create(cuenta.correo(), encoder.encode(env.getProperty(cuenta.variable())),
                    cuenta.nombre(), cuenta.rol());
            try {
                usuarios.save(usuario);
                log.info("Semilla nominal: cuenta creada ({}, {})", cuenta.correo(), cuenta.rol());
            } catch (DuplicateKeyException ex) {
                // Otra instancia pudo ganar la carrera. No ocultar otro conflicto de índice.
                if (!usuarios.existsByCorreo(cuenta.correo())) throw ex;
                log.info("Semilla nominal: cuenta ya existente ({})", cuenta.correo());
            }
        }
        log.info("Semilla nominal terminada; las cuentas preexistentes no se modificaron");
    }
}
