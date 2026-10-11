package com.monpoint.gateway;

import com.monpoint.gateway.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Punto único de entrada de MonPoint (H-002, H-003, H-009). Enruta por prefijo a los servicios registrados en
 * Eureka, valida el token localmente y responde CORS al cliente web. Es el único componente que publica puerto.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

}
