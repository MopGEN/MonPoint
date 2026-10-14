package com.monpoint.auth.controller;

import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.exception.CorreoDuplicadoException;
import com.monpoint.auth.exception.ModificacionRolPropioException;
import com.monpoint.auth.exception.OperacionInvalidaException;
import com.monpoint.auth.exception.UsuarioNoEncontradoException;
import com.monpoint.auth.model.Rol;
import com.monpoint.auth.security.JwtService;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paso 7.3 — API de gestión de usuarios: reglas por rol del filtro y traducción a ProblemDetail
 * de las excepciones del servicio (H-011, H-013).
 */
@WebMvcTest(UsuarioController.class)
@Import(SeguridadDePruebaConfig.class)
class UsuarioControllerTest {

    private static final UserPrincipal ADMIN = new UserPrincipal("admin-1", "admin@monpoint.com", Rol.ADMIN);
    private static final UserPrincipal VENDEDOR = new UserPrincipal("vend-1", "vale@monpoint.com", Rol.VENDEDOR);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UsuarioService usuarioService;

    // ---------------------------------------------------------------- PUT /auth/usuarios/{id}

    @Test
    void modificar_AutoModificacionDeRol_Retorna400ProblemDetail() throws Exception {
        when(usuarioService.modificar(eq("admin-1"), any(), eq(ADMIN))).thenThrow(new ModificacionRolPropioException());

        mockMvc.perform(put("/auth/usuarios/admin-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Admin", "correo": "admin@monpoint.com", "rol": "VENDEDOR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Operación no permitida"))
                .andExpect(jsonPath("$.detail").value("No puedes cambiar tu propio rol"));
    }

    @Test
    void modificar_VendedorAOtroUsuario_Retorna403DesdeElServicio() throws Exception {
        when(usuarioService.modificar(eq("admin-1"), any(), eq(VENDEDOR)))
                .thenThrow(new AccessDeniedException("Solo puedes modificar tu propio perfil"));

        mockMvc.perform(put("/auth/usuarios/admin-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "X", "correo": "admin@monpoint.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Solo puedes modificar tu propio perfil"));
    }

    @Test
    void modificar_ConCorreoDeOtroUsuario_Retorna409() throws Exception {
        when(usuarioService.modificar(eq("vend-1"), any(), eq(VENDEDOR)))
                .thenThrow(new CorreoDuplicadoException("admin@monpoint.com"));

        mockMvc.perform(put("/auth/usuarios/vend-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Vale", "correo": "admin@monpoint.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un usuario con el correo admin@monpoint.com"));
    }

    @Test
    void modificar_ChoqueConElIndiceUnico_Retorna409SinExponerDetallesDeMongo() throws Exception {
        when(usuarioService.modificar(eq("vend-1"), any(), eq(VENDEDOR))).thenThrow(new DuplicateKeyException(
                "E11000 duplicate key error collection: auth_db.usuarios index: correo dup key"));

        mockMvc.perform(put("/auth/usuarios/vend-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Vale", "correo": "otro@monpoint.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe un usuario con ese correo"))
                .andExpect(content().string(not(containsString("E11000"))));
    }

    @Test
    void modificar_SinCorreo_Retorna400ConElCampoFallido() throws Exception {
        mockMvc.perform(put("/auth/usuarios/vend-1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "Vale"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").value("El correo no puede estar vacío"));
        verifyNoInteractions(usuarioService);
    }

    @Test
    void modificar_IdInexistente_Retorna404() throws Exception {
        when(usuarioService.modificar(eq("no-existe"), any(), eq(ADMIN)))
                .thenThrow(new UsuarioNoEncontradoException("no-existe"));

        mockMvc.perform(put("/auth/usuarios/no-existe")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre": "X", "correo": "x@monpoint.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Usuario no encontrado"));
    }

    // ---------------------------------------------------------------- PATCH /auth/usuarios/{id}/desactivar

    @Test
    void desactivar_AutoDesactivacion_Retorna400ProblemDetail() throws Exception {
        doThrow(new OperacionInvalidaException("El administrador no puede desactivar su propia cuenta"))
                .when(usuarioService).desactivar("admin-1", ADMIN);

        mockMvc.perform(patch("/auth/usuarios/admin-1/desactivar").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El administrador no puede desactivar su propia cuenta"));
    }

    @Test
    void desactivar_OtroUsuarioComoAdmin_Retorna204SinCuerpo() throws Exception {
        mockMvc.perform(patch("/auth/usuarios/vend-1/desactivar").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(usuarioService).desactivar("vend-1", ADMIN);
    }

    @Test
    void desactivar_ComoVendedor_Retorna403SinLlegarAlServicio() throws Exception {
        mockMvc.perform(patch("/auth/usuarios/vend-1/desactivar").header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
        verifyNoInteractions(usuarioService);
    }

    // ---------------------------------------------------------------- GET /auth/usuarios y rutas inválidas

    @Test
    void listarActivos_ComoAdmin_Retorna200ConLaLista() throws Exception {
        when(usuarioService.listarActivos()).thenReturn(List.of(
                new UsuarioResponse("admin-1", "Administrador", "admin@monpoint.com", "ADMIN", true)));

        mockMvc.perform(get("/auth/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correo").value("admin@monpoint.com"));
    }

    @Test
    void listarActivos_ComoVendedor_Retorna403() throws Exception {
        mockMvc.perform(get("/auth/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(VENDEDOR)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test
    void rutaInexistente_Retorna404EnProblemDetail() throws Exception {
        mockMvc.perform(get("/auth/usuarios/vend-1/no-existe").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Ruta no encontrada"));
    }

    @Test
    void metodoNoSoportado_Retorna405ConElHeaderAllow() throws Exception {
        mockMvc.perform(delete("/auth/usuarios").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(jsonPath("$.title").value("Método no permitido"));
    }

    private String bearer(UserPrincipal principal) {
        return "Bearer " + jwtService.generarToken(principal);
    }
}
