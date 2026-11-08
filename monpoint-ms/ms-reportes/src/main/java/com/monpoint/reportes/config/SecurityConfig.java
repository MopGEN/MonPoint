package com.monpoint.reportes.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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
//Solo por agregar Spring Security, todas las rutas pedirían usuario y contraseña. Esta clase quita ese comportamiento.
//
//@Configuration y @Bean: Spring ejecuta el metodo y administra el objeto que devuelve. Bean y Configuration vienen de esos dos primeros imports.
//HttpSecurity: es el constructor de reglas de seguridad; Spring te lo pasa como parámetro.
//AbstractHttpConfigurer::disable: apaga la función indicada.
//csrf: protege sesiones de navegador basadas en cookies, y aquí no hay.
//formLogin, httpBasic, logout: aquí nadie inicia sesión.
//STATELESS: no guarda sesiones; cada petición trae sus propias cabeceras.
//anyRequest().permitAll(): Spring Security no bloquea ninguna ruta. Las reglas de rol van en los servicios, porque ahí puedes responder 403 con tu formato de error.