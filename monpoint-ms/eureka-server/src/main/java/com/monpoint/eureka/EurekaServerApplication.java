package com.monpoint.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Servidor de descubrimiento de MonPoint (H-001). Cada microservicio se registra aquí al arrancar
 * y el API Gateway lo localiza por nombre ({@code lb://ms-auth}) en lugar de por dirección fija.
 * <p>
 * {@code @EnableEurekaServer} es lo que convierte esta aplicación en un servidor Eureka: activa el
 * registro de instancias, la API REST {@code /eureka/**} y el panel web en {@code /}.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(EurekaServerApplication.class, args);
	}

}
