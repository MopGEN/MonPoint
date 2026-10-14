package com.monpoint.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validación local de los tokens que emite ms-auth (H-003), con JJWT 0.12.6.
 * <p>
 * Aplica el mismo contrato que {@code JwtService.validarToken} de ms-auth ({@code docs/CONTRATO_TOKEN.md}):
 * firma HS256 con la clave compartida, vigencia, y los claims {@code sub}, {@code userId} y {@code rol}.
 * Nunca consulta a ms-auth: todo se decide con la clave y el propio token.
 */
@Component
public class JwtValidador {

	private static final String CLAIM_USER_ID = "userId";
	private static final String CLAIM_ROL = "rol";
	private static final Set<String> ROLES = Set.of("ADMIN", "VENDEDOR");

	private final SecretKey clave;

	public JwtValidador(JwtProperties properties) {
		this.clave = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Verifica firma y vigencia y devuelve la identidad del token.
	 * <p>
	 * {@code verifyWith} hace que JJWT rechace también los tokens sin firma ({@code "alg": "none"}).
	 *
	 * @throws JwtException             token expirado, alterado, sin firma, mal formado o sin los claims requeridos
	 * @throws IllegalArgumentException token vacío
	 */
	public IdentidadUsuario validar(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(clave)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		String correo = claims.getSubject();
		String userId = claims.get(CLAIM_USER_ID, String.class);
		String rol = claims.get(CLAIM_ROL, String.class);
		if (correo == null || userId == null || rol == null) {
			throw new MalformedJwtException("El token no contiene los claims sub, userId y rol");
		}
		if (!ROLES.contains(rol)) {
			throw new MalformedJwtException("El token trae un rol que no es ADMIN ni VENDEDOR");
		}
		return new IdentidadUsuario(userId, correo, rol);
	}
}
