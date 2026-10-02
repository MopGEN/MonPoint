package com.monpoint.auth.config;

import com.monpoint.auth.security.CustomAccessDeniedHandler;
import com.monpoint.auth.security.CustomAuthenticationEntryPoint;
import com.monpoint.auth.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Cadena de seguridad 100% stateless: cada petición se autentica solo con su JWT.
 * <p>
 * Sin {@code AuthenticationManager} ni {@code UserDetailsService}: las credenciales del login
 * se validan a mano en {@code AuthServiceImpl} (Fase 4).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthenticationFilter,
                                                   CustomAuthenticationEntryPoint authenticationEntryPoint,
                                                   CustomAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                // Sin cookies de sesión no hay CSRF que proteger: el token viaja en un header.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        // Página de error interna de Spring Boot: sin esto, un 404 o un 500 llegaría como 401.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/register").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/auth/usuarios").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/auth/usuarios/{id}/desactivar").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/auth/me").authenticated()
                        // Quién puede modificar a quién lo decide UsuarioServiceImpl.
                        .requestMatchers(HttpMethod.PUT, "/auth/usuarios/{id}").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * El filtro JWT es un {@code @Component}; sin esto Spring Boot también lo registraría como
     * filtro del servidor, fuera de la cadena de seguridad.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registro = new FilterRegistrationBean<>(filter);
        registro.setEnabled(false);
        return registro;
    }
}
