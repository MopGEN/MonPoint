package com.monpoint.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Levanta el contexto con las rutas reales de {@code application.properties} y comprueba que se cargaron.
 * Protege contra el prefijo antiguo {@code spring.cloud.gateway.routes}, que Gateway 5.0 ignora sin dar error.
 * Eureka se apaga: la prueba no debe depender de un servidor de descubrimiento.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

	@Autowired
	private RouteDefinitionLocator rutas;

	@Test
	void contextLoads_ConLasSeisRutasPorEurekaYSinStripPrefix() {
		List<RouteDefinition> definiciones = rutas.getRouteDefinitions().collectList().block();

		assertThat(definiciones)
				.extracting(RouteDefinition::getId)
				.containsExactlyInAnyOrder("ms-auth", "ms-inventario", "ms-ventas", "ms-clientes", "ms-reportes",
						"ms-notificaciones");
		assertThat(definiciones).allSatisfy(ruta -> {
			assertThat(ruta.getUri().getScheme()).isEqualTo("lb");
			assertThat(ruta.getFilters()).isEmpty();
		});
		assertThat(definiciones)
				.filteredOn(ruta -> ruta.getId().equals("ms-auth"))
				.singleElement()
				.satisfies(ruta -> {
					assertThat(ruta.getUri()).hasToString("lb://ms-auth");
					assertThat(ruta.getPredicates()).singleElement()
							.satisfies(predicado -> assertThat(predicado.getArgs()).containsValue("/auth/**"));
				});
	}

}
