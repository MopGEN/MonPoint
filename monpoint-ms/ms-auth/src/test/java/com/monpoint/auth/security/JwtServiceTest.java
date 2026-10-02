package com.monpoint.auth.security;

import com.monpoint.auth.model.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Paso 7.1 — JwtService sin Spring: generación, claims, firma HS256 y rechazo de tokens inválidos.
 */
class JwtServiceTest {

    /** Misma clave de desarrollo: 52 bytes, suficiente para que JJWT eligiera HS384 si no se forzara HS256. */
    private static final String CLAVE = "cambiar_esta_clave_debe_tener_al_menos_32_caracteres";
    private static final SecretKey LLAVE = Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8));
    private static final UserPrincipal VENDEDOR = new UserPrincipal("u-1", "vale@monpoint.com", Rol.VENDEDOR);

    private final JwtService jwtService = new JwtService(new JwtProperties(CLAVE, 86_400_000));

    @Test
    void generarToken_YValidarlo_RecuperaIdCorreoYRol() {
        String token = jwtService.generarToken(VENDEDOR);

        assertThat(jwtService.validarToken(token)).isEqualTo(VENDEDOR);
    }

    @Test
    void generarToken_IncluyeLosClaimsDelContrato() {
        Claims claims = leer(jwtService.generarToken(VENDEDOR)).getPayload();

        assertThat(claims.getSubject()).isEqualTo("vale@monpoint.com");
        assertThat(claims.get("userId", String.class)).isEqualTo("u-1");
        assertThat(claims.get("rol", String.class)).isEqualTo("VENDEDOR");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void generarToken_FirmaConHS256AunqueLaClaveTenga52Bytes() {
        Jws<Claims> jws = leer(jwtService.generarToken(VENDEDOR));

        assertThat(CLAVE.getBytes(StandardCharsets.UTF_8)).hasSize(52);
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
    }

    @Test
    void validarToken_ConVigenciaDe1ms_LanzaExpiredJwtException() throws InterruptedException {
        JwtService efimero = new JwtService(new JwtProperties(CLAVE, 1));
        String token = efimero.generarToken(VENDEDOR);

        // "exp" se guarda en segundos: con vigencia de 1 ms hay que dejar pasar más de 1 s para
        // que el vencimiento sea seguro; si no, la prueba fallaría al azar.
        Thread.sleep(1_100);

        assertThatThrownBy(() -> efimero.validarToken(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void validarToken_FirmadoConOtraClave_LanzaSignatureException() {
        JwtService ajeno = new JwtService(new JwtProperties("otra_clave_distinta_con_al_menos_32_caracteres", 60_000));

        String tokenAjeno = ajeno.generarToken(VENDEDOR);

        assertThatThrownBy(() -> jwtService.validarToken(tokenAjeno)).isInstanceOf(SignatureException.class);
    }

    @Test
    void validarToken_ConPayloadAlterado_LanzaSignatureException() {
        String[] partes = jwtService.generarToken(VENDEDOR).split("\\.");
        String payloadAdmin = base64Url("{\"sub\":\"vale@monpoint.com\",\"userId\":\"u-1\",\"rol\":\"ADMIN\"}");

        String alterado = partes[0] + "." + payloadAdmin + "." + partes[2];

        assertThatThrownBy(() -> jwtService.validarToken(alterado)).isInstanceOf(SignatureException.class);
    }

    @Test
    void validarToken_SinFirma_LanzaUnsupportedJwtException() {
        String sinFirma = base64Url("{\"alg\":\"none\"}") + "."
                + base64Url("{\"sub\":\"admin@monpoint.com\",\"userId\":\"x\",\"rol\":\"ADMIN\"}") + ".";

        assertThatThrownBy(() -> jwtService.validarToken(sinFirma)).isInstanceOf(UnsupportedJwtException.class);
    }

    @Test
    void validarToken_SinClaimUserId_LanzaMalformedJwtException() {
        String token = firmado(Jwts.builder().subject("vale@monpoint.com").claim("rol", "VENDEDOR"));

        assertThatThrownBy(() -> jwtService.validarToken(token)).isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void validarToken_ConRolDesconocido_LanzaIllegalArgumentException() {
        String token = firmado(Jwts.builder().subject("x@monpoint.com").claim("userId", "x").claim("rol", "OTRO"));

        assertThatThrownBy(() -> jwtService.validarToken(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_ConClaveDeMenosDe32Bytes_LanzaWeakKeyException() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("demasiado-corta", 60_000)))
                .isInstanceOf(WeakKeyException.class);
    }

    private static Jws<Claims> leer(String token) {
        return Jwts.parser().verifyWith(LLAVE).build().parseSignedClaims(token);
    }

    private static String firmado(io.jsonwebtoken.JwtBuilder builder) {
        return builder.expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(LLAVE, Jwts.SIG.HS256)
                .compact();
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
