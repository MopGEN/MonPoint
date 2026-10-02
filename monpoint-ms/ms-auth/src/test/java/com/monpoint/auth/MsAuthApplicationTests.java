package com.monpoint.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta el contexto completo contra MongoDB. El registro en Eureka se apaga: la prueba
 * no debe depender de que haya un servidor de descubrimiento corriendo.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class MsAuthApplicationTests {

    @Test
    void contextLoads() {
    }

}
