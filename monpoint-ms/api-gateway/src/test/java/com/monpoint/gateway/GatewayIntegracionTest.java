package com.monpoint.gateway;

import com.monpoint.gateway.security.TokensDePrueba;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Gateway completo en un puerto aleatorio, frente a un servicio de destino falso.
 * <p>
 * El destino es un servidor reactor-netty (ya viene con el Gateway) que responde con las cabeceras que recibió.
 * Así se comprueba qué llega realmente aguas abajo: la identidad del token, la eliminación de cabeceras falsas y
 * el {@code X-Request-ID}. Las rutas reales apuntan a {@code lb://…} y necesitan Eureka; aquí se reemplaza la
 * lista completa por dos rutas hacia el destino falso (Spring sustituye las listas enteras, no elemento por elemento).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"eureka.client.enabled=false",
		"jwt.secret=" + TokensDePrueba.CLAVE,
		"monpoint.cors.origenes=http://localhost:5173,http://localhost:3000"
})
class GatewayIntegracionTest {

	private static final String ORIGEN_PERMITIDO = "http://localhost:5173";
	private static final AtomicInteger LLAMADAS_AL_DESTINO = new AtomicInteger();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final DisposableServer DESTINO = HttpServer.create()
			.port(0)
			.handle((peticion, respuesta) -> {
				LLAMADAS_AL_DESTINO.incrementAndGet();
				// Como ms-auth: devuelve el X-Request-ID que recibió.
				String requestId = peticion.requestHeaders().get("X-Request-ID");
				return respuesta.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
						.header("X-Request-ID", requestId != null ? requestId : "")
						.sendString(Mono.just(eco(peticion)));
			})
			.bindNow();

	@Value("${local.server.port}")
	private int puerto;

	private WebTestClient cliente;

	@DynamicPropertySource
	static void rutasHaciaElDestinoFalso(DynamicPropertyRegistry registro) {
		String destino = "http://localhost:" + DESTINO.port();
		String rutas = "spring.cloud.gateway.server.webflux.routes";
		registro.add(rutas + "[0].id", () -> "ms-auth");
		registro.add(rutas + "[0].uri", () -> destino);
		registro.add(rutas + "[0].predicates[0]", () -> "Path=/auth/**");
		registro.add(rutas + "[1].id", () -> "ms-inventario");
		registro.add(rutas + "[1].uri", () -> destino);
		registro.add(rutas + "[1].predicates[0]", () -> "Path=/inventario/**");
	}

	@AfterAll
	static void apagarDestino() {
		DESTINO.disposeNow();
	}

	@BeforeEach
	void crearCliente() {
		cliente = WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build();
	}

	// ---------------------------------------------------------------- rutas públicas

	@Test
	void loginEsPublico_LlegaAlDestinoSinToken() {
		cliente.post().uri("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"correo\":\"admin@monpoint.com\",\"password\":\"Admin123!\"}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.ruta").isEqualTo("/auth/login")
				.jsonPath("$.metodo").isEqualTo("POST");
	}

	@Test
	void actuatorHealth_RespondeUpSinToken() {
		cliente.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP");
	}

	// ---------------------------------------------------------------- 401

	@Test
	void rutaProtegidaSinToken_Responde401ProblemDetail_YNoLlegaAlDestino() {
		int llamadasAntes = LLAMADAS_AL_DESTINO.get();

		cliente.get().uri("/auth/me")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
				.expectBody()
				.jsonPath("$.status").isEqualTo(401)
				.jsonPath("$.title").isEqualTo("No autenticado")
				.jsonPath("$.detail").isEqualTo("Se requiere un token válido. Inicia sesión para obtener uno.")
				.jsonPath("$.instance").isEqualTo("/auth/me")
				.jsonPath("$.requestId").isNotEmpty();

		assertThat(LLAMADAS_AL_DESTINO.get()).isEqualTo(llamadasAntes);
	}

	@Test
	void tokenExpirado_Responde401() {
		conToken(TokensDePrueba.expirado()).expectStatus().isUnauthorized();
	}

	@Test
	void tokenAlterado_Responde401() {
		conToken(TokensDePrueba.alterado()).expectStatus().isUnauthorized();
	}

	@Test
	void tokenSinFirma_Responde401() {
		conToken(TokensDePrueba.sinFirma()).expectStatus().isUnauthorized();
	}

	@Test
	void authorizationSinBearer_Responde401() {
		cliente.get().uri("/auth/me")
				.header(HttpHeaders.AUTHORIZATION, "Basic YWRtaW46YWRtaW4=")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	// ---------------------------------------------------------------- identidad

	@Test
	void tokenValido_PropagaIdentidadYConservaAuthorization() {
		String token = TokensDePrueba.valido("6abf387781233486cd272c8e", "VENDEDOR");

		conToken(token)
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$['X-User-Id']").isEqualTo(List.of("6abf387781233486cd272c8e"))
				.jsonPath("$['X-User-Role']").isEqualTo(List.of("VENDEDOR"))
				.jsonPath("$.Authorization").isEqualTo(List.of("Bearer " + token));
	}

	@Test
	void prefijoBearerEnMinusculas_SeAcepta() {
		cliente.get().uri("/inventario/productos")
				.header(HttpHeaders.AUTHORIZATION, "bearer " + TokensDePrueba.valido("u-1", "ADMIN"))
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$['X-User-Role']").isEqualTo(List.of("ADMIN"));
	}

	@Test
	void cabecerasXUserFalsas_ConTokenValido_SeSustituyenPorLasDelToken() {
		cliente.get().uri("/inventario/productos")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + TokensDePrueba.valido("u-vendedor", "VENDEDOR"))
				.header("X-User-Id", "u-atacante")
				.header("X-User-Role", "ADMIN")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$['X-User-Id']").isEqualTo(List.of("u-vendedor"))
				.jsonPath("$['X-User-Role']").isEqualTo(List.of("VENDEDOR"));
	}

	@Test
	void cabecerasXUserFalsas_EnRutaPublica_SeEliminan() {
		cliente.post().uri("/auth/login")
				.header("X-User-Id", "u-atacante")
				.header("x-user-role", "ADMIN")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$['X-User-Id']").doesNotExist()
				.jsonPath("$['X-User-Role']").doesNotExist();
	}

	// ---------------------------------------------------------------- CORS

	@Test
	void preflightDesdeOrigenPermitido_RespondeCorsSinToken_YNoLlegaAlDestino() {
		int llamadasAntes = LLAMADAS_AL_DESTINO.get();

		cliente.options().uri("/auth/me")
				.header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO)
				.expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
						metodos -> assertThat(metodos).contains("GET", "POST", "PATCH", "DELETE"))
				.expectHeader().value(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						cabeceras -> assertThat(cabeceras.toLowerCase()).contains("authorization", "content-type"));

		assertThat(LLAMADAS_AL_DESTINO.get()).isEqualTo(llamadasAntes);
	}

	@Test
	void preflightDesdeOrigenNoPermitido_SeRechaza() {
		cliente.options().uri("/auth/me")
				.header(HttpHeaders.ORIGIN, "http://sitio-ajeno.com")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
				.exchange()
				.expectStatus().isForbidden();
	}

	@Test
	void respuesta401AlNavegador_LlevaCabecerasCors() {
		cliente.get().uri("/auth/me")
				.header(HttpHeaders.ORIGIN, ORIGEN_PERMITIDO)
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEN_PERMITIDO);
	}

	// ---------------------------------------------------------------- enrutamiento y X-Request-ID

	@Test
	void prefijoNoDeclarado_Responde404() {
		cliente.get().uri("/noexiste/recurso")
				.exchange()
				.expectStatus().isNotFound();
	}

	@Test
	void requestIdValido_SePropagaYVuelveUnaSolaVez() {
		cliente.post().uri("/auth/login")
				.header("X-Request-ID", "prueba-gateway-123")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().values("X-Request-ID", valores -> assertThat(valores).containsExactly("prueba-gateway-123"))
				.expectBody()
				.jsonPath("$['X-Request-ID']").isEqualTo(List.of("prueba-gateway-123"));
	}

	@Test
	void requestIdInvalido_SeReemplazaPorUnoNuevo() {
		cliente.post().uri("/auth/login")
				.header("X-Request-ID", "valor con espacios")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{}")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().value("X-Request-ID", valor -> assertThat(valor)
						.isNotEqualTo("valor con espacios")
						.matches("[0-9a-f-]{36}"));
	}

	// ---------------------------------------------------------------- utilidades

	private WebTestClient.ResponseSpec conToken(String token) {
		return cliente.get().uri("/inventario/productos")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.exchange();
	}

	/** Lo que recibió el destino: método, ruta y las cabeceras que interesan (solo las presentes). */
	private static String eco(HttpServerRequest peticion) {
		Map<String, Object> eco = new LinkedHashMap<>();
		eco.put("metodo", peticion.method().name());
		eco.put("ruta", peticion.uri());
		for (String cabecera : List.of("X-User-Id", "X-User-Role", "X-Request-ID", HttpHeaders.AUTHORIZATION)) {
			List<String> valores = peticion.requestHeaders().getAll(cabecera);
			if (!valores.isEmpty()) {
				eco.put(cabecera, valores);
			}
		}
		return JSON.writeValueAsString(eco);
	}
}
