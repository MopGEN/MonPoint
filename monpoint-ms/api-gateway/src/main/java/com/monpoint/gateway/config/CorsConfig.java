package com.monpoint.gateway.config;

import com.monpoint.gateway.filter.RequestIdWebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * CORS del Gateway (H-009): el único componente al que el navegador llama directamente.
 * <p>
 * Es un {@code WebFilter}, así que corre <b>antes</b> del enrutamiento y del filtro de JWT:
 * <ul>
 *   <li>la verificación previa ({@code OPTIONS}) se responde aquí mismo, sin pedir token;</li>
 *   <li>un origen fuera de la lista se rechaza con 403 antes de llegar a ningún servicio;</li>
 *   <li>los 401 del Gateway salen con las cabeceras CORS, así el cliente web puede leerlos y cerrar la sesión.</li>
 * </ul>
 */
@Configuration
public class CorsConfig {

	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE + 1)
	public CorsWebFilter corsWebFilter(@Value("${monpoint.cors.origenes}") List<String> origenes) {
		CorsConfiguration configuracion = new CorsConfiguration();
		configuracion.setAllowedOrigins(origenes);
		configuracion.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuracion.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
				RequestIdWebFilter.HEADER));
		// El navegador solo deja leer a JavaScript las cabeceras expuestas explícitamente.
		configuracion.setExposedHeaders(List.of(RequestIdWebFilter.HEADER));
		// El navegador recuerda la respuesta de la verificación previa durante una hora.
		configuracion.setMaxAge(Duration.ofHours(1));

		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/**", configuracion);
		return new CorsWebFilter(fuente);
	}
}
