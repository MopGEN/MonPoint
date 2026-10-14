package com.monpoint.clientes.controller;

import com.monpoint.clientes.config.SecurityConfig;
import com.monpoint.clientes.dto.response.ClienteResponse;
import com.monpoint.clientes.exception.AccesoDenegadoException;
import com.monpoint.clientes.exception.RecursoNoEncontradoException;
import com.monpoint.clientes.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
@WebMvcTest(ClienteController.class)
@Import(SecurityConfig.class)
class ClienteControllerTest {

    private static final String ID_USUARIO = "6abf387781233486cd272c8e";
    private static final String ID_CLIENTE = "6abf387781233486cd272c99";
    private static final Instant AHORA = Instant.parse("2026-10-13T10:00:00Z");
    private static final ClienteResponse ANA = new ClienteResponse(ID_CLIENTE, "Ana Pérez", "ana@correo.com",
            "3312345678", "Calle 1", AHORA, AHORA);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteService clienteService;

    // ------------------------------------------------------------------ POST /clientes

    @Test
    void crear_ComoVendedor_Responde201ConElClienteCreado() throws Exception {
        when(clienteService.crear(any(), any())).thenReturn(ANA);

        mockMvc.perform(post("/clientes")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Pérez","correo":"ana@correo.com","telefono":"3312345678","direccion":"Calle 1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID_CLIENTE))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.fechaRegistro").exists());
    }

    @Test
    void crear_ComoAdmin_Responde201() throws Exception {
        when(clienteService.crear(any(), any())).thenReturn(ANA);

        mockMvc.perform(post("/clientes")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Pérez\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void crear_ConNombreVacioYCorreoInvalido_Responde400ListandoTodosLosCampos() throws Exception {
        mockMvc.perform(post("/clientes")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"correo\":\"no-es-un-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.correo").exists())
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void crear_SinCabecerasDeIdentidad_Responde401() throws Exception {
        mockMvc.perform(post("/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
    }

    @Test
    void crear_ConRolDesconocido_Responde400() throws Exception {
        mockMvc.perform(post("/clientes")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "SUPERUSUARIO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_ConCuerpoQueNoEsJson_Responde400() throws Exception {
        mockMvc.perform(post("/clientes")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo de la petición inválido"));
    }

    // ------------------------------------------------------------------ GET /clientes

    @Test
    void listar_ConNombreParcial_DevuelveLosCoincidentes() throws Exception {
        when(clienteService.listar("ana")).thenReturn(List.of(ANA));

        mockMvc.perform(get("/clientes")
                        .param("nombre", "ana")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listar_SinNombre_DevuelveTodos() throws Exception {
        when(clienteService.listar(null)).thenReturn(List.of(ANA, ANA));

        mockMvc.perform(get("/clientes")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listar_SinCabeceras_Responde401() throws Exception {
        mockMvc.perform(get("/clientes"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ GET /clientes/{id}

    @Test
    void obtener_Existente_Responde200() throws Exception {
        when(clienteService.obtener(ID_CLIENTE)).thenReturn(ANA);

        mockMvc.perform(get("/clientes/" + ID_CLIENTE)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("ana@correo.com"));
    }

    @Test
    void obtener_Inexistente_Responde404ConProblemDetail() throws Exception {
        when(clienteService.obtener("noexiste"))
                .thenThrow(new RecursoNoEncontradoException("No existe el cliente con id noexiste"));

        mockMvc.perform(get("/clientes/noexiste")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.instance").value("/clientes/noexiste"));
    }

    // ------------------------------------------------------------------ PUT /clientes/{id}

    @Test
    void modificar_Existente_Responde200() throws Exception {
        when(clienteService.modificar(eq(ID_CLIENTE), any(), any())).thenReturn(ANA);

        mockMvc.perform(put("/clientes/" + ID_CLIENTE)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Pérez\",\"correo\":\"ana@correo.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID_CLIENTE));
    }

    @Test
    void modificar_Inexistente_Responde404() throws Exception {
        when(clienteService.modificar(eq("noexiste"), any(), any()))
                .thenThrow(new RecursoNoEncontradoException("No existe el cliente con id noexiste"));

        mockMvc.perform(put("/clientes/noexiste")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void modificar_ConCorreoInvalido_Responde400() throws Exception {
        mockMvc.perform(put("/clientes/" + ID_CLIENTE)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"xxx\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.correo").exists());
    }

    // ------------------------------------------------------------------ DELETE /clientes/{id}

    @Test
    void eliminar_ComoAdmin_Responde204() throws Exception {
        mockMvc.perform(delete("/clientes/" + ID_CLIENTE)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminar_ComoVendedor_Responde403ConProblemDetail() throws Exception {
        doThrow(new AccesoDenegadoException("Solo un ADMIN puede dar de baja clientes"))
                .when(clienteService).eliminar(eq(ID_CLIENTE), any());

        mockMvc.perform(delete("/clientes/" + ID_CLIENTE)
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void eliminar_Inexistente_Responde404() throws Exception {
        doThrow(new RecursoNoEncontradoException("No existe el cliente con id noexiste"))
                .when(clienteService).eliminar(eq("noexiste"), any());

        mockMvc.perform(delete("/clientes/noexiste")
                        .header("X-User-Id", ID_USUARIO)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNotFound());
    }
}
