package com.monpoint.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Se excluye {@link UserDetailsServiceAutoConfiguration}: sin ella Spring Boot crearía un usuario
 * en memoria con contraseña generada. ms-auth no usa {@code UserDetailsService}; el login valida
 * las credenciales contra MongoDB en {@code AuthServiceImpl}.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MsAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAuthApplication.class, args);
    }

}
