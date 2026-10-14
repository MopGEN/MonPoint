package com.monpoint.auth.config;

import com.monpoint.auth.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Beans de seguridad que no dependen de la cadena de filtros HTTP.
 * <p>
 * Separado de {@code SecurityConfig} para que {@code DataInitializer} pueda cifrar
 * la contraseña del admin inicial sin acoplarse a la configuración de rutas. También
 * habilita {@link JwtProperties}, que usa {@code JwtService} tanto en el filtro como en el login.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
