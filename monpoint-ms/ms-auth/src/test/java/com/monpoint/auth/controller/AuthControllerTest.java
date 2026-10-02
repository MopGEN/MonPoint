package com.monpoint.auth.controller;

import com.monpoint.auth.dto.request.LoginRequest;
import com.monpoint.auth.dto.response.AuthResponse;
import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.exception.CredencialesInvalidasException;
import com.monpoint.auth.model.Rol;
import com.monpoint.auth.security.JwtProperties;
import com.monpoint.auth.security.JwtService;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.service.AuthService;
import com.monpoint.auth.service.UsuarioService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paso 7.3 — API de autenticación con la seguridad real y tokens firmados por {@link JwtService}.
 * Los servicios se simulan: sus reglas ya se prueban en {@code AuthServiceTest} y {@code UsuarioServiceTest}.
 */
@WebMvcTest(AuthController.class)
@Import(SeguridadDePruebaConfig.class)
class AuthControllerTest {

    private static final UserPrincipal ADMIN = new UserPrincipal("admin-1", "admin@monpoint.com", Rol.ADMIN);
    private static final UserPrincipal VENDEDOR = new UserPrincipal("vend-1", "vale@monpoint.com", Rol.VENDEDOR);
    private static final String REGISTRO_VALIDO = """
            {"nombre": "Vale", "correo": "vale@monpoint.com", "password": "Secreto1!", "rol": "VENDEDOR"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UsuarioService usuarioService;

    // ---------------------------------------------------------------- POST /auth/login (pública)

    @Test
    void login_ConCredencialesValidas_Retorna200ConToken() throws Exception {
        when(authService.login(new LoginRequest("admin@monpoint.com", "Admin123!"))).thenReturn(
                new AuthResponse("jwt.firmado", "Bearer", "admin@monpoint.com", "Administrador", "ADMIN", 86_400_000L));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo": "admin@monpoint.com", "password": "Admin123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.firmado"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }

    @Test
    void login_ConCredencialesIncorrectas_Retorna401ProblemDetailOpaco() throws Exception {
        when(authService.login(any())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/auth/login")
                        .header("X-Request-ID", "req-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo": "admin@monpoint.com", "password": "Mala123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Credenciales incorrectas"))
                .andExpect(jsonPath("$.requestId").value("req-401"))
                .andExpect(header().string("X-Request-ID", "req-401"));
    }

    @Test
    void login_ConCorreoVacio_Retorna400ConLosCamposQueFallaron() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"correo": "", "password": "x"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.campos.correo").value("El correo no puede estar vacío"));
        verifyNoInteractions(authService);
    }

    // ---------------------------------------------------------------- POST /auth/register (solo ADMIN)

    @Test
    void register_SinToken_Retorna401DelEntryPoint() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(usuarioService);
    }

    @Test
    void register_ConTokenDeVendedor_Retorna403ConCuerpoCompleto() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.detail").value("Tu rol no tiene permiso para realizar esta operación."))
                .andExpect(jsonPath("$.instance").value("/auth/register"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(usuarioService);
    }

    @Test
    void register_ConTokenDeAdmin_Retorna201YEntregaAlServicioElEjecutorDelToken() throws Exception {
        when(usuarioService.registrar(any(), eq(ADMIN)))
                .thenReturn(new UsuarioResponse("nuevo-1", "Vale", "vale@monpoint.com", "VENDEDOR", true));

        mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("nuevo-1"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void register_ConRolDesconocido_Retorna400IndicandoLosValoresPermitidos() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "O", "correo": "o@monpoint.com", "password": "Secreto1!", "rol": "OTRO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El campo 'rol' solo admite: [ADMIN, VENDEDOR]"));
        verifyNoInteractions(usuarioService);
    }

    // ---------------------------------------------------------------- GET /auth/me (autenticado)

    @Test
    void me_ConTokenValido_DevuelveElPerfilDelUsuarioDelToken() throws Exception {
        when(usuarioService.obtenerPorId("vend-1"))
                .thenReturn(new UsuarioResponse("vend-1", "Vale", "vale@monpoint.com", "VENDEDOR", true));

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("vale@monpoint.com"))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void me_ConTokenExpirado_Retorna401SinError500() throws Exception {
        String expirado = Jwts.builder()
                .subject("admin@monpoint.com")
                .claim("userId", "admin-1")
                .claim("rol", "ADMIN")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();

        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void me_ConTokenCorrupto_Retorna401SinError500() throws Exception {
        mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("No autenticado"));
        verifyNoInteractions(usuarioService);
    }

    private String bearer(UserPrincipal principal) {
        return "Bearer " + jwtService.generarToken(principal);
    }
}
