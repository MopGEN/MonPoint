package com.monpoint.auth.config;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.repository.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Semillero inicial (H-073): si la colección {@code usuarios} está vacía, crea el primer
 * administrador para que el sistema sea utilizable desde el primer arranque.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private static final String ADMIN_CORREO = "admin@monpoint.com";
    private static final String ADMIN_NOMBRE = "Administrador";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataInitializer(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${ADMIN_DEFAULT_PASSWORD:Admin123!}") String adminPassword) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            log.info("Semillero omitido: la colección usuarios ya tiene datos");
            return;
        }

        Usuario admin = Usuario.create(ADMIN_CORREO, passwordEncoder.encode(adminPassword), ADMIN_NOMBRE, Rol.ADMIN);
        try {
            usuarioRepository.save(admin);
            log.info("Semillero: administrador inicial creado ({})", ADMIN_CORREO);
        } catch (DuplicateKeyException ex) {
            // Otra instancia de ms-auth arrancó a la vez y ganó la carrera; el índice único lo frenó.
            log.info("Semillero omitido: el administrador inicial ya existe");
        }
    }
}
