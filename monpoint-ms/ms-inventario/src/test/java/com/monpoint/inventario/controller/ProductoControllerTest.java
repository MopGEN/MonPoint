package com.monpoint.inventario.controller;

import com.monpoint.inventario.config.SecurityConfig;
import com.monpoint.inventario.dto.response.ProductoResponse;
import com.monpoint.inventario.exception.AccesoDenegadoException;
import com.monpoint.inventario.exception.RecursoNoEncontradoException;
import com.monpoint.inventario.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una prueba por endpoint y por regla (RNF-14). Manda X-User-Id / X-User-Role como lo haría el Gateway.
 */
@WebMvcTest(ProductoController.class)
@Import(SecurityConfig.class)
class ProductoControllerTest {

    private static final String ID_USUARIO = "6abf387781233486cd272c8e";
    private static final String ID_PRODUCTO = "6abf387781233486cd272caa";
    private static final Instant AHORA = Instant.parse("2026-10-13T10:00:00Z");
    private static final ProductoResponse ACEITE = new ProductoResponse(ID_PRODUCTO, "Aceite 1L", null,
            new BigDecimal("45.50"), 10, 5, true, AHORA, AHORA);
    private static final String CUERPO_VALIDO = """
            {"nombre":"Aceite 1L","precio":45.50,"existencia":10}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    // ------------------------------------------------------------------ POST /inventario/productos

    @Test
    void crear_ComoAdmin_Responde201ConIdYPrecioExacto() throws Exception {
        when(productoService.crear(any(), any())).thenReturn(ACEITE);

        mockMvc.perform(post("/inventario/productos")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID_PRODUCTO))
                .andExpect(jsonPath("$.precio").value(45.50))
                .andExpect(jsonPath("$.existencia").value(10))
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void crear_ComoVendedor_Responde403ConProblemDetail() throws Exception {
        when(productoService.crear(any(), any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede dar de alta productos"));

        mockMvc.perform(post("/inventario/productos")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void crear_ConPrecioYExistenciaNegativos_Responde400ListandoAmbosCampos() throws Exception {
        mockMvc.perform(post("/inventario/productos")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Aceite\",\"precio\":-1,\"existencia\":-3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.campos.precio").exists())
                .andExpect(jsonPath("$.campos.existencia").exists());
    }

    @Test
    void crear_SinNombreNiPrecio_Responde400() throws Exception {
        mockMvc.perform(post("/inventario/productos")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"existencia\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.precio").exists());
    }

    @Test
    void crear_SinCabecerasDeIdentidad_Responde401() throws Exception {
        mockMvc.perform(post("/inventario/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ GET /inventario/productos

    @Test
    void listar_ComoVendedor_Responde200ConLaExistencia() throws Exception {
        when(productoService.listar()).thenReturn(List.of(ACEITE));

        mockMvc.perform(get("/inventario/productos")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Aceite 1L"))
                .andExpect(jsonPath("$[0].existencia").value(10));
    }

    @Test
    void listar_SinCabeceras_Responde401() throws Exception {
        mockMvc.perform(get("/inventario/productos"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ GET /inventario/productos/{id}

    @Test
    void obtener_Existente_Responde200() throws Exception {
        when(productoService.obtener(ID_PRODUCTO)).thenReturn(ACEITE);

        mockMvc.perform(get("/inventario/productos/" + ID_PRODUCTO)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umbralStock").value(5));
    }

    @Test
    void obtener_Inexistente_Responde404ConProblemDetail() throws Exception {
        when(productoService.obtener("noexiste"))
                .thenThrow(new RecursoNoEncontradoException("No existe el producto con id noexiste"));

        mockMvc.perform(get("/inventario/productos/noexiste")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.instance").value("/inventario/productos/noexiste"));
    }

    // ------------------------------------------------------------------ PUT /inventario/productos/{id}

    @Test
    void modificar_ComoAdmin_Responde200() throws Exception {
        when(productoService.modificar(eq(ID_PRODUCTO), any(), any())).thenReturn(ACEITE);

        mockMvc.perform(put("/inventario/productos/" + ID_PRODUCTO)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_PRODUCTO));
    }

    @Test
    void modificar_ComoVendedor_Responde403() throws Exception {
        when(productoService.modificar(eq(ID_PRODUCTO), any(), any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede modificar productos"));

        mockMvc.perform(put("/inventario/productos/" + ID_PRODUCTO)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void modificar_Inexistente_Responde404() throws Exception {
        when(productoService.modificar(eq("noexiste"), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe el producto con id noexiste"));

        mockMvc.perform(put("/inventario/productos/noexiste")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO))
                .andExpect(status().isNotFound());
    }

    @Test
    void modificar_ConPrecioNegativo_Responde400() throws Exception {
        mockMvc.perform(put("/inventario/productos/" + ID_PRODUCTO)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Aceite\",\"precio\":-0.01,\"existencia\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.precio").exists());
    }

    // ------------------------------------------------------------------ DELETE /inventario/productos/{id}

    @Test
    void darDeBaja_ComoAdmin_Responde204() throws Exception {
        mockMvc.perform(delete("/inventario/productos/" + ID_PRODUCTO)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void darDeBaja_ComoVendedor_Responde403() throws Exception {
        doThrow(new AccesoDenegadoException("Solo un ADMIN puede dar de baja productos"))
                .when(productoService).darDeBaja(eq(ID_PRODUCTO), any());

        mockMvc.perform(delete("/inventario/productos/" + ID_PRODUCTO)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void darDeBaja_Inexistente_Responde404() throws Exception {
        doThrow(new RecursoNoEncontradoException("No existe el producto con id noexiste"))
                .when(productoService).darDeBaja(eq("noexiste"), any());

        mockMvc.perform(delete("/inventario/productos/noexiste")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }
}
