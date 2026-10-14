package com.monpoint.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tokens como los que emite ms-auth (HS256, claims {@code sub}, {@code userId}, {@code rol}) y sus variantes
 * inválidas, para probar el Gateway sin depender de ms-auth.
 */
public final class TokensDePrueba {

	/** Clave de las pruebas; las de integración la configuran como {@code jwt.secret}. */
	public static final String CLAVE = "clave_de_pruebas_del_api_gateway_32_bytes";

	private TokensDePrueba() {
	}

	public static String valido(String userId, String rol) {
		return firmar(CLAVE, claims("vendedor@monpoint.com", userId, rol), Instant.now().plusSeconds(3600));
	}

	public static String expirado() {
		return firmar(CLAVE, claims("vendedor@monpoint.com", "u-1", "VENDEDOR"), Instant.now().minusSeconds(60));
	}

	public static String firmadoConOtraClave() {
		return firmar("otra_clave_que_no_es_la_del_gateway_32_bytes", claims("vendedor@monpoint.com", "u-1", "VENDEDOR"),
				Instant.now().plusSeconds(3600));
	}

	/** Token con {@code "alg": "none"}: sin firma. */
	public static String sinFirma() {
		return Jwts.builder()
				.claims(claims("admin@monpoint.com", "u-1", "ADMIN"))
				.expiration(Date.from(Instant.now().plusSeconds(3600)))
				.compact();
	}

	/** Token válido de un VENDEDOR al que se le cambió el payload por uno de ADMIN, conservando la firma original. */
	public static String alterado() {
		String[] partes = valido("u-1", "VENDEDOR").split("\\.");
		String payloadAdmin = """
				{"sub":"vendedor@monpoint.com","userId":"u-1","rol":"ADMIN","exp":%d}"""
				.formatted(Instant.now().plusSeconds(3600).getEpochSecond());
		String payload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payloadAdmin.getBytes(StandardCharsets.UTF_8));
		return partes[0] + "." + payload + "." + partes[2];
	}

	public static String sinUserId() {
		return firmar(CLAVE, claims("vendedor@monpoint.com", null, "VENDEDOR"), Instant.now().plusSeconds(3600));
	}

	public static String conRol(String rol) {
		return firmar(CLAVE, claims("vendedor@monpoint.com", "u-1", rol), Instant.now().plusSeconds(3600));
	}

	private static Map<String, Object> claims(String sub, String userId, String rol) {
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("sub", sub);
		if (userId != null) {
			claims.put("userId", userId);
		}
		claims.put("rol", rol);
		return claims;
	}

	private static String firmar(String clave, Map<String, Object> claims, Instant expiracion) {
		return Jwts.builder()
				.claims(claims)
				.issuedAt(new Date())
				.expiration(Date.from(expiracion))
				.signWith(Keys.hmacShaKeyFor(clave.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
				.compact();
	}
}
