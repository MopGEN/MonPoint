package com.monpoint.inventario;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo contra MongoDB (docker compose up -d mongodb). El registro en Eureka se apaga y
 * el listener de RabbitMQ no arranca: la prueba no debe depender de Eureka ni del broker.
 */
@SpringBootTest(properties = {
		"eureka.client.enabled=false",
		"spring.rabbitmq.listener.simple.auto-startup=false"
})
class MsInventarioApplicationTests {

	@Test
	void contextLoads() {
	}

}
