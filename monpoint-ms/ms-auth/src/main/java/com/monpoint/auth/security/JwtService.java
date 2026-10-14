package com.monpoint.auth.security;

import com.monpoint.auth.model.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emisión y validación de tokens JWT con JJWT 0.12.6 (H-010, H-014).
 * <p>
 * El API Gateway valida estos mismos tokens con la misma clave, así que los claims
 * ({@code sub}, {@code userId}, {@code rol}) y el algoritmo son un contrato compartido.
 */
@Service
public class JwtService {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_ROL = "rol";

    private final SecretKey secretKey;
    private final long expiracionMs;

    public JwtService(JwtProperties properties) {
        // Lanza WeakKeyException al arrancar si la clave tiene menos de 256 bits (32 bytes).
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiracionMs = properties.expiration();
    }

    public String generarToken(UserPrincipal principal) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(principal.correo())
                .claim(CLAIM_USER_ID, principal.id())
                .claim(CLAIM_ROL, principal.rol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(expiracionMs)))
                // Explícito: con una clave de 48 bytes o más (la de desarrollo tiene 52),
                // signWith(key) sin algoritmo elegiría HS384 en silencio.
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifica firma y vigencia, y reconstruye la identidad sin consultar la BD.
     *
     * @throws JwtException             token expirado, alterado, mal formado o sin los claims requeridos
     * @throws IllegalArgumentException token vacío o con un rol que no existe
     */
    public UserPrincipal validarToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String correo = claims.getSubject();
        String userId = claims.get(CLAIM_USER_ID, String.class);
        String rol = claims.get(CLAIM_ROL, String.class);
        if (correo == null || userId == null || rol == null) {
            throw new MalformedJwtException("El token no contiene los claims sub, userId y rol");
        }
        return new UserPrincipal(userId, correo, Rol.valueOf(rol));
    }

    public long getExpiracionMs() {
        return expiracionMs;
    }
}
