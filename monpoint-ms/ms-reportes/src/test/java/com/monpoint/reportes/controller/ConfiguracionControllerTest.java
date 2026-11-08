package com.monpoint.reportes.controller;

import com.monpoint.reportes.config.SecurityConfig;
import com.monpoint.reportes.dto.response.ConfiguracionResponse;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RecursoNoEncontradoException;
import com.monpoint.reportes.service.ConfiguracionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Manda X-User-Id / X-User-Role como lo haría el Gateway. La regla de rol vive en el servicio (mock aquí).
 */
@WebMvcTest(ConfiguracionController.class)
@Import(SecurityConfig.class)
class ConfiguracionControllerTest {

    private static final String RUTA = "/reportes/configuracion";
    private static final String ID_USUARIO = "6abf387781233486cd272c8e";
    private static final ConfiguracionResponse CONFIG = new ConfiguracionResponse("Monpoint", "MON850101AB1",
            "Calle 1", "3312345678", Instant.parse("2026-11-08T18:00:00Z"));
    private static final String CUERPO_VALIDO = """
            {"nombreEmpresa":"Monpoint","rfc":"MON850101AB1","direccion":"Calle 1","telefono":"3312345678"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConfiguracionService configuracionService;

    // ------------------------------------------------------------------ GET

    @Test
    void obtener_ComoVendedor_Responde200() throws Exception {
        when(configuracionService.obtener()).thenReturn(CONFIG);

        mockMvc.perform(get(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreEmpresa").value("Monpoint"))
                .andExpect(jsonPath("$.rfc").value("MON850101AB1"));
    }

    @Test
    void obtener_SinConfiguracion_Responde404ProblemDetailConRequestId() throws Exception {
        when(configuracionService.obtener())
                .thenThrow(new RecursoNoEncontradoException("La configuración de la empresa todavía no se ha registrado"));

        mockMvc.perform(get(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "ADMIN")
                        .header("X-Request-ID", "prueba-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.requestId").value("prueba-404"))
                .andExpect(header().string("X-Request-ID", "prueba-404"));
    }

    @Test
    void obtener_SinCabecerasDelGateway_Responde401() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));

        verifyNoInteractions(configuracionService);
    }

    @Test
    void obtener_ConRolDesconocido_Responde400() throws Exception {
        mockMvc.perform(get(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "OTRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
    }

    // ------------------------------------------------------------------ PUT

    @Test
    void guardar_ComoAdmin_Responde200() throws Exception {
        when(configuracionService.guardar(any(), any())).thenReturn(CONFIG);

        mockMvc.perform(put(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreEmpresa").value("Monpoint"))
                .andExpect(jsonPath("$.fechaActualizacion").exists());
    }

    @Test
    void guardar_ComoVendedor_Responde403() throws Exception {
        when(configuracionService.guardar(any(), any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede modificar la configuración de la empresa"));

        mockMvc.perform(put(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "VENDEDOR")
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void guardar_ConCamposInvalidos_Responde400ListandoTodos() throws Exception {
        mockMvc.perform(put(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreEmpresa\":\"\",\"rfc\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.campos.nombreEmpresa").exists())
                .andExpect(jsonPath("$.campos.rfc").value("El RFC no tiene un formato válido"));

        verifyNoInteractions(configuracionService);
    }

    @Test
    void guardar_ConJsonMalFormado_Responde400() throws Exception {
        mockMvc.perform(put(RUTA).header("X-User-Id", ID_USUARIO).header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Cuerpo de la petición inválido"));
    }

    @Test
    void guardar_SinCabecerasYConCuerpoInvalido_Responde401AntesQue400() throws Exception {
        mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
