package com.monpoint.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Garantiza un {@code X-Request-ID} por petición (RNF-13): el que mandó el cliente si es válido, o uno nuevo.
 * <p>
 * Lo reenvía al servicio de destino, que lo imprime en sus logs, y lo devuelve en la respuesta. Corre antes que
 * todo lo demás para que también lo lleven los 401 del Gateway y las respuestas de CORS. Aplica la misma regla
 * que el {@code RequestIdFilter} de ms-auth, así ambos aceptan exactamente los mismos valores.
 */
@Component
public class RequestIdWebFilter implements WebFilter, Ordered {

	public static final String HEADER = "X-Request-ID";

	private static final String ATRIBUTO = RequestIdWebFilter.class.getName() + ".requestId";
	/** Descarta valores con saltos de línea u otros caracteres que permitirían inyectar texto en logs o cabeceras. */
	private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String requestId = resolver(exchange.getRequest().getHeaders().getFirst(HEADER));

		ServerHttpRequest peticion = exchange.getRequest().mutate().header(HEADER, requestId).build();
		ServerWebExchange conRequestId = exchange.mutate().request(peticion).build();
		conRequestId.getAttributes().put(ATRIBUTO, requestId);
		// Se fija justo antes de enviar la respuesta, con set y no add: ms-auth también devuelve
		// X-Request-ID y el Gateway copia sus cabeceras, así que de otro modo saldría dos veces.
		conRequestId.getResponse().beforeCommit(() -> {
			conRequestId.getResponse().getHeaders().set(HEADER, requestId);
			return Mono.empty();
		});
		return chain.filter(conRequestId);
	}

	/** El identificador de la petición en curso: el del cliente o el generado por este filtro. */
	public static String obtener(ServerWebExchange exchange) {
		String requestId = exchange.getAttribute(ATRIBUTO);
		return requestId != null ? requestId : resolver(exchange.getRequest().getHeaders().getFirst(HEADER));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	private static String resolver(String recibido) {
		return recibido != null && FORMATO_VALIDO.matcher(recibido).matches()
				? recibido
				: UUID.randomUUID().toString();
	}
}
