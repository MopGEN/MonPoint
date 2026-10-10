package com.monpoint.eureka;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paso 8.5 — Servidor Eureka real en un puerto aleatorio, consultado por HTTP igual que lo hacen el
 * healthcheck de Docker, el navegador y los microservicios.
 * <p>
 * No usa MockMvc: la API {@code /eureka/**} la sirve Jersey como filtro del servlet, fuera de
 * Spring MVC, así que solo una petición HTTP real la ejercita completa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerIntegracionTest {

	private static final String APP = "SERVICIO-PRUEBA";
	private static final String INSTANCIA = "servicio-prueba:127.0.0.1:9999";

	/** Lo mínimo que Eureka exige para aceptar un registro; un cliente real envía más campos. */
	private static final String REGISTRO = """
			{"instance": {
			  "instanceId": "servicio-prueba:127.0.0.1:9999",
			  "hostName": "127.0.0.1",
			  "app": "SERVICIO-PRUEBA",
			  "ipAddr": "127.0.0.1",
			  "status": "UP",
			  "port": {"$": 9999, "@enabled": "true"},
			  "dataCenterInfo": {
			    "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
			    "name": "MyOwn"
			  }
			}}
			""";

	private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

	@Value("${local.server.port}")
	private int puerto;

	@Test
	void actuatorHealth_RespondeUpSinDetalles() throws Exception {
		HttpResponse<String> respuesta = enviar(peticion("/actuator/health").GET());

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat((String) JsonPath.read(respuesta.body(), "$.status")).isEqualTo("UP");
		assertThat(respuesta.body()).doesNotContain("components");
	}

	@Test
	void panelWeb_RespondeElDashboardDeEureka() throws Exception {
		HttpResponse<String> respuesta = enviar(peticion("/").GET());

		assertThat(respuesta.statusCode()).isEqualTo(200);
		assertThat(respuesta.headers().firstValue("Content-Type")).hasValueSatisfying(
				tipo -> assertThat(tipo).startsWith("text/html"));
		assertThat(respuesta.body()).contains("Instances currently registered with Eureka");
	}

	@Test
	void instanciaRegistrada_ApareceEnElPanel_YDesapareceAlDarseDeBaja() throws Exception {
		HttpResponse<String> alta = enviar(peticion("/eureka/apps/" + APP)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(REGISTRO)));
		assertThat(alta.statusCode()).isEqualTo(204);

		// Se consulta la instancia por id: /eureka/apps pasa por la caché de respuestas y
		// tarda hasta 30 s en reflejar un cambio. La consulta por id y el panel leen el registro.
		HttpResponse<String> consulta = enviar(peticion("/eureka/apps/" + APP + "/" + INSTANCIA)
				.header("Accept", "application/json").GET());
		assertThat(consulta.statusCode()).isEqualTo(200);
		assertThat((String) JsonPath.read(consulta.body(), "$.instance.status")).isEqualTo("UP");
		assertThat(enviar(peticion("/").GET()).body()).contains(APP);

		HttpResponse<String> baja = enviar(peticion("/eureka/apps/" + APP + "/" + INSTANCIA).DELETE());
		assertThat(baja.statusCode()).isEqualTo(200);

		assertThat(enviar(peticion("/eureka/apps/" + APP + "/" + INSTANCIA).GET()).statusCode()).isEqualTo(404);
		assertThat(enviar(peticion("/").GET()).body()).doesNotContain(APP);
	}

	private HttpRequest.Builder peticion(String ruta) {
		return HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta));
	}

	private HttpResponse<String> enviar(HttpRequest.Builder peticion) throws IOException, InterruptedException {
		return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
	}

}
