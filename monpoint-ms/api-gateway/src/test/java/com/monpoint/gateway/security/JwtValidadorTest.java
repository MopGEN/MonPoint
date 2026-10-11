package com.monpoint.gateway.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las reglas del contrato del token ({@code ms-auth/docs/CONTRATO_TOKEN.md} §4): cuándo un token se acepta y
 * cuándo el Gateway debe responder 401.
 */
class JwtValidadorTest {

	private final JwtValidador validador = new JwtValidador(new JwtProperties(TokensDePrueba.CLAVE));

	@Test
	void tokenValido_DevuelveLaIdentidadDelToken() {
		IdentidadUsuario identidad = validador.validar(TokensDePrueba.valido("6abf387781233486cd272c8e", "ADMIN"));

		assertThat(identidad.userId()).isEqualTo("6abf387781233486cd272c8e");
		assertThat(identidad.correo()).isEqualTo("vendedor@monpoint.com");
		assertThat(identidad.rol()).isEqualTo("ADMIN");
	}

	@Test
	void tokenExpirado_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(TokensDePrueba.expirado()))
				.isInstanceOf(ExpiredJwtException.class);
	}

	@Test
	void tokenFirmadoConOtraClave_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(TokensDePrueba.firmadoConOtraClave()))
				.isInstanceOf(SignatureException.class);
	}

	@Test
	void tokenConPayloadAlterado_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(TokensDePrueba.alterado()))
				.isInstanceOf(SignatureException.class);
	}

	@Test
	void tokenSinFirma_AlgNone_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(TokensDePrueba.sinFirma()))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void tokenSinUserId_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(TokensDePrueba.sinUserId()))
				.isInstanceOf(MalformedJwtException.class);
	}

	@Test
	void tokenConRolDesconocido_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(TokensDePrueba.conRol("OTRO")))
				.isInstanceOf(MalformedJwtException.class);
	}

	@Test
	void tokenVacio_SeRechaza() {
		assertThatThrownBy(() -> validador.validar(""))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void claveDeMenosDe32Bytes_ImpideArrancar() {
		assertThatThrownBy(() -> new JwtProperties("clave_corta"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("32 bytes");
	}
}
