package com.monpoint.ventas.controller;

import com.monpoint.ventas.config.SecurityConfig;
import com.monpoint.ventas.dto.request.VentaRequest;
import com.monpoint.ventas.dto.response.VentaResponse;
import com.monpoint.ventas.exception.AccesoDenegadoException;
import com.monpoint.ventas.exception.VentaInvalidaException;
import com.monpoint.ventas.model.EstadoVenta;
import com.monpoint.ventas.service.VentaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VentaController.class)
@Import(SecurityConfig.class)
class VentaControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean VentaService servicio;

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "VENDEDOR"})
    void responde202EIgnoraImportesYEstadoDelCliente(String rol) throws Exception {
        when(servicio.registrar(any(), any())).thenReturn(new VentaResponse("venta", 42, EstadoVenta.PENDIENTE));
        mvc.perform(post("/ventas").header("X-User-Id", "u").header("X-User-Role", rol)
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"total":0.01,"folio":1,"estado":"CONFIRMADA","lineas":[
                  {"productoId":"p","cantidad":3,"precioUnitario":0.01,"nombreProducto":"Falso","subtotal":0.03}]}
                """))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value("venta"))
                .andExpect(jsonPath("$.folio").value(42)).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        var entrada = ArgumentCaptor.forClass(VentaRequest.class);
        verify(servicio).registrar(entrada.capture(), any());
        assertThat(entrada.getValue().lineas()).containsExactly(new VentaRequest.LineaRequest("p", 3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"lineas\":[]}", "{\"lineas\":[null]}",
            "{\"lineas\":[{\"productoId\":\"\",\"cantidad\":1}]}",
            "{\"lineas\":[{\"productoId\":\"p\",\"cantidad\":0}]}",
            "{\"lineas\":[{\"productoId\":\"p\",\"cantidad\":-1}]}",
            "{\"lineas\":[{\"productoId\":\"p\"}]}",
            "{\"lineas\":[{\"productoId\":\"p\",\"cantidad\":1.5}]}",
            "{\"lineas\":[{\"productoId\":\"p\",\"cantidad\":2147483648}]}", "no-json"})
    void entradaInvalidaEs400SinInvocarServicio(String json) throws Exception {
        mvc.perform(post("/ventas").header("X-User-Id", "u").header("X-User-Role", "VENDEDOR")
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
        verifyNoInteractions(servicio);
    }

    @Test void sinIdentidadEs401() throws Exception {
        mvc.perform(post("/ventas").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(servicio);
    }

    @Test void rolNoAutorizadoEs403() throws Exception {
        when(servicio.registrar(any(), any())).thenThrow(new AccesoDenegadoException("Rol no permitido"));
        mvc.perform(post("/ventas").header("X-User-Id", "u").header("X-User-Role", "LECTOR")
                .contentType(MediaType.APPLICATION_JSON).content("{\"lineas\":[{\"productoId\":\"p\",\"cantidad\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test void conservaLiteralElMensajeParaLaCaja() throws Exception {
        when(servicio.registrar(any(), any())).thenThrow(new VentaInvalidaException(VentaService.CLIENTE_NO_DISPONIBLE));
        mvc.perform(post("/ventas").header("X-User-Id", "u").header("X-User-Role", "VENDEDOR")
                .contentType(MediaType.APPLICATION_JSON).content("{\"clienteId\":\"c\",\"lineas\":[{\"productoId\":\"p\",\"cantidad\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(VentaService.CLIENTE_NO_DISPONIBLE));
    }
}
