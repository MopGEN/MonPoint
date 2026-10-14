package com.monpoint.auth.controller;

import com.monpoint.auth.config.SecurityBeansConfig;
import com.monpoint.auth.config.SecurityConfig;
import com.monpoint.auth.security.CustomAccessDeniedHandler;
import com.monpoint.auth.security.CustomAuthenticationEntryPoint;
import com.monpoint.auth.security.JwtService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Seguridad real para los slices {@code @WebMvcTest}: cadena stateless, reglas por rol, JWT y
 * handlers 401/403.
 * <p>
 * El slice ya trae por su cuenta los filtros ({@code RequestIdFilter}, {@code JwtAuthenticationFilter})
 * y el {@code GlobalExceptionHandler}, pero no las clases {@code @Configuration} ni los
 * {@code @Component}/{@code @Service} de seguridad: esos se importan aquí.
 */
@TestConfiguration
@Import({
        SecurityConfig.class,
        SecurityBeansConfig.class,
        JwtService.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class SeguridadDePruebaConfig {
}
