package com.monpoint.clientes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo contra MongoDB (docker compose up -d mongodb). El registro en Eureka se
 * apaga: la prueba no debe depender de que haya un servidor de descubrimiento corriendo.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class MsClientesApplicationTests {

	@Test
	void contextLoads() {
	}

}
