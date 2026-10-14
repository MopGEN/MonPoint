package com.monpoint.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Propiedades {@code jwt.*}: la clave HS256 que comparte con ms-auth (ms-auth firma, el Gateway verifica).
 * <p>
 * Se valida al arrancar: sin clave, o con menos de 32 bytes, el Gateway no inicia. La comprobación va en el
 * constructor porque el Gateway no trae un proveedor de Bean Validation y no vale la pena agregarlo por una regla.
 *
 * @param secret clave en texto plano (UTF-8, no Base64), idéntica a la de ms-auth
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret) {

	/** HS256 exige una clave de al menos 256 bits. */
	public static final int BYTES_MINIMOS = 32;

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < BYTES_MINIMOS) {
			throw new IllegalArgumentException("jwt.secret (JWT_SECRET) debe tener al menos " + BYTES_MINIMOS
					+ " bytes: HS256 exige una clave de 256 bits");
		}
	}
}
