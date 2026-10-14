package com.monpoint.eureka;

import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta el contexto completo. Además de que arranque, comprueba que exista el registro de
 * instancias: sin {@code @EnableEurekaServer} el contexto también cargaría, pero no sería un
 * servidor Eureka, y una prueba vacía no lo detectaría.
 */
@SpringBootTest
class EurekaServerApplicationTests {

	@Autowired
	private ApplicationContext contexto;

	@Test
	void contextLoads_ConElRegistroDeEurekaActivo() {
		assertThat(contexto.getBeanNamesForType(PeerAwareInstanceRegistry.class)).isNotEmpty();
	}

}
