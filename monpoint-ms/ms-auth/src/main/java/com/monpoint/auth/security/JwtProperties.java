package com.monpoint.auth.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades {@code jwt.*}. Se validan al arrancar: sin clave o con vigencia inválida la app no inicia.
 *
 * @param secret     clave HS256 en texto plano (UTF-8), mínimo 32 caracteres; idéntica en el API Gateway
 * @param expiration vigencia del token en milisegundos
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(@NotBlank String secret, @Positive long expiration) {
}
