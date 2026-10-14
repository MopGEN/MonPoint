package com.monpoint.gateway.filter;

import com.monpoint.gateway.security.IdentidadUsuario;
import com.monpoint.gateway.security.JwtValidador;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;

/**
 * Valida el token de cada petición enrutada, sin llamar a ms-auth (H-003, {@code CONTRATO_TOKEN.md} §5).
 * <p>
 * Es un {@code GlobalFilter}: solo actúa sobre peticiones que coinciden con una ruta. Por eso
 * {@code /actuator/health} (lo atiende Actuator) y los prefijos no declarados (404) nunca llegan aquí, y el
 * {@code OPTIONS} de CORS lo responde antes {@code CorsWebFilter}.
 * <ol>
 *   <li>Elimina siempre los {@code X-User-Id}/{@code X-User-Role} que mande el cliente (anti-suplantación).</li>
 *   <li>Deja pasar sin token {@code POST /auth/login} y {@code OPTIONS}.</li>
 *   <li>Con un token válido agrega {@code X-User-Id} y {@code X-User-Role}; sin token o con uno inválido responde
 *       401 en {@code ProblemDetail}, igual que ms-auth, y la petición no llega al servicio.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationGlobalFilter implements GlobalFilter, Ordered {

	public static final String HEADER_USER_ID = "X-User-Id";
	public static final String HEADER_USER_ROLE = "X-User-Role";

	private static final String RUTA_LOGIN = "/auth/login";
	private static final String PREFIJO_BEARER = "Bearer ";

	private final JwtValidador jwtValidador;
	private final JsonMapper jsonMapper;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest peticion = exchange.getRequest();
		if (esPublica(peticion)) {
			return chain.filter(conIdentidad(exchange, null));
		}

		String token = extraerToken(peticion);
		if (token == null) {
			log.debug("Petición sin token a {}", peticion.getPath());
			return responderNoAutenticado(exchange);
		}
		IdentidadUsuario identidad;
		try {
			identidad = jwtValidador.validar(token);
		} catch (JwtException | IllegalArgumentException ex) {
			// Solo el tipo de error: el token nunca se escribe en el log.
			log.debug("Token rechazado en {}: {}", peticion.getPath(), ex.getClass().getSimpleName());
			return responderNoAutenticado(exchange);
		}
		return chain.filter(conIdentidad(exchange, identidad));
	}

	@Override
	public int getOrder() {
		// Antes de los filtros propios del Gateway que reenvían la petición al servicio.
		return -100;
	}

	private static boolean esPublica(ServerHttpRequest peticion) {
		HttpMethod metodo = peticion.getMethod();
		return HttpMethod.OPTIONS.equals(metodo)
				|| (HttpMethod.POST.equals(metodo) && RUTA_LOGIN.equals(peticion.getPath().value()));
	}

	/** El token del encabezado {@code Authorization: Bearer <token>}; el prefijo no distingue mayúsculas, como en ms-auth. */
	private static String extraerToken(ServerHttpRequest peticion) {
		String autorizacion = peticion.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (autorizacion == null
				|| !autorizacion.regionMatches(true, 0, PREFIJO_BEARER, 0, PREFIJO_BEARER.length())) {
			return null;
		}
		String token = autorizacion.substring(PREFIJO_BEARER.length()).trim();
		return token.isEmpty() ? null : token;
	}

	/**
	 * Quita la identidad que haya puesto el cliente y, si hay token válido, agrega la del token. El
	 * {@code Authorization} se conserva: ms-auth vuelve a validar el token por su cuenta.
	 */
	private static ServerWebExchange conIdentidad(ServerWebExchange exchange, IdentidadUsuario identidad) {
		ServerHttpRequest peticion = exchange.getRequest().mutate().headers(cabeceras -> {
			cabeceras.remove(HEADER_USER_ID);
			cabeceras.remove(HEADER_USER_ROLE);
			if (identidad != null) {
				cabeceras.set(HEADER_USER_ID, identidad.userId());
				cabeceras.set(HEADER_USER_ROLE, identidad.rol());
			}
		}).build();
		return exchange.mutate().request(peticion).build();
	}

	/** 401 con el mismo cuerpo que el {@code CustomAuthenticationEntryPoint} de ms-auth (contrato §7). */
	private Mono<Void> responderNoAutenticado(ServerWebExchange exchange) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Se requiere un token válido. Inicia sesión para obtener uno.");
		problema.setTitle("No autenticado");
		problema.setInstance(URI.create(exchange.getRequest().getPath().value()));
		problema.setProperty("requestId", RequestIdWebFilter.obtener(exchange));

		ServerHttpResponse respuesta = exchange.getResponse();
		respuesta.setStatusCode(HttpStatus.UNAUTHORIZED);
		respuesta.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		respuesta.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		byte[] cuerpo = jsonMapper.writeValueAsBytes(problema);
		return respuesta.writeWith(Mono.just(respuesta.bufferFactory().wrap(cuerpo)));
	}
}
