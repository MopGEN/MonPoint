package com.monpoint.inventario.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security sin estado y sin bloquear rutas (guía G11 de ms-auth).
 * <p>
 * La autenticación ya la hizo el Gateway: este servicio NO valida tokens. El control de acceso
 * es el de la guía G5: cabeceras {@code X-User-Id}/{@code X-User-Role} ➔ {@code UsuarioActual}
 * ➔ regla de rol en el servicio ➔ 403. Esta cadena existe solo para que Spring Security no
 * pida usuario y contraseña en todas las rutas.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
