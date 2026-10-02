package com.monpoint.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Se excluye {@link UserDetailsServiceAutoConfiguration}: sin ella Spring Boot crearía un usuario
 * en memoria con contraseña generada. ms-auth no usa {@code UserDetailsService}; el login valida
 * las credenciales contra MongoDB en {@code AuthServiceImpl}.
 * <p>
 * {@link EnableDiscoveryClient} registra el servicio en Eureka como {@code MS-AUTH}, el nombre
 * con el que el API Gateway lo encuentra ({@code lb://ms-auth}).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableDiscoveryClient
public class MsAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAuthApplication.class, args);
    }

}
