package com.monpoint.reportes.controller;

import com.monpoint.reportes.config.SecurityConfig;
import com.monpoint.reportes.dto.response.AgregadoVentasResponse;
import com.monpoint.reportes.dto.response.AgregadoVentasResponse.Punto;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.model.Periodo;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.AgregacionVentasService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReporteVentasController.class)
@Import(SecurityConfig.class)
class ReporteVentasControllerTest {

    private static final String RUTA = "/reportes/ventas/agregado";
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgregacionVentasService agregacionVentasService;

    @Test
    void agregado_ComoAdmin_DevuelveLaSerie() throws Exception {
        when(agregacionVentasService.agregar(any(), any(), any(), any())).thenReturn(new AgregadoVentasResponse(
                Periodo.DIA, DESDE, HASTA, "America/Mexico_City", new BigDecimal("70.50"),
                List.of(new Punto(DESDE, new BigDecimal("70.50")), new Punto(HASTA, BigDecimal.ZERO))));

        mockMvc.perform(conIdentidad("ADMIN", "?periodo=DIA&desde=2026-11-01&hasta=2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo").value("DIA"))
                .andExpect(jsonPath("$.desde").value("2026-11-01"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Mexico_City"))
                .andExpect(jsonPath("$.total").value(70.50))
                .andExpect(jsonPath("$.puntos.length()").value(2))
                .andExpect(jsonPath("$.puntos[0].inicio").value("2026-11-01"))
                .andExpect(jsonPath("$.puntos[1].total").value(0));
        verify(agregacionVentasService).agregar(Periodo.DIA, DESDE, HASTA,
                new UsuarioActual("6abf387781233486cd272c8e", Rol.ADMIN));
    }

    @Test
    void agregado_ComoVendedor_Responde403() throws Exception {
        when(agregacionVentasService.agregar(any(), any(), any(), any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede consultar los reportes de ventas"));

        mockMvc.perform(conIdentidad("VENDEDOR", "?periodo=DIA&desde=2026-11-01&hasta=2026-11-02"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void agregado_SinIdentidad_Responde401AntesDeValidarParametros() throws Exception {
        mockMvc.perform(get(RUTA + "?periodo=SEMANA"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(agregacionVentasService);
    }

    @Test
    void agregado_RangoInvertido_Responde400() throws Exception {
        when(agregacionVentasService.agregar(any(), any(), any(), any()))
                .thenThrow(new RangoInvalidoException("La fecha 'hasta' no puede ser anterior a 'desde'"));

        mockMvc.perform(conIdentidad("ADMIN", "?periodo=DIA&desde=2026-11-02&hasta=2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Rango inválido"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "?periodo=SEMANA&desde=2026-11-01&hasta=2026-11-02",
            "?periodo=DIA&desde=01-11-2026&hasta=2026-11-02",
            "?periodo=DIA&desde=2026-02-30&hasta=2026-11-02"
    })
    void agregado_ValorInvalido_Responde400(String consulta) throws Exception {
        mockMvc.perform(conIdentidad("ADMIN", consulta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
        verifyNoInteractions(agregacionVentasService);
    }

    @Test
    void agregado_SinFechas_Responde400() throws Exception {
        mockMvc.perform(conIdentidad("ADMIN", "?periodo=DIA"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro faltante"))
                .andExpect(jsonPath("$.detail").value("Falta el parámetro 'desde'"));
        verifyNoInteractions(agregacionVentasService);
    }

    private static MockHttpServletRequestBuilder conIdentidad(String rol, String consulta) {
        return get(RUTA + consulta).header("X-User-Id", "6abf387781233486cd272c8e").header("X-User-Role", rol);
    }
}
