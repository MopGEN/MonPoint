package com.monpoint.ventas.controller;

import com.monpoint.ventas.config.SecurityConfig;
import com.monpoint.ventas.dto.response.VentaDetalleResponse;
import com.monpoint.ventas.dto.response.VentaDetalleResponse.LineaDetalle;
import com.monpoint.ventas.exception.AccesoDenegadoException;
import com.monpoint.ventas.exception.VentaInvalidaException;
import com.monpoint.ventas.exception.VentaNoEncontradaException;
import com.monpoint.ventas.model.EstadoVenta;
import com.monpoint.ventas.security.UsuarioActual;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Consulta de una venta por identificador (actividad 5.2).
 * <p>
 * Esta es la única vía por la que el cliente web conoce el desenlace de la saga: el registro responde 202
 * con la venta en PENDIENTE y la resolución llega después por mensajería.
 */
@WebMvcTest(VentaController.class)
@Import(SecurityConfig.class)
class VentaConsultaTest {
    @Autowired MockMvc mvc;
    @MockitoBean VentaService servicio;

    private static final String ID = "68f1a2b3c4d5e6f700000001";

    private static VentaDetalleResponse cancelada() {
        return new VentaDetalleResponse(ID, 1042L, EstadoVenta.CANCELADA,
                "68f1a2b3c4d5e6f700000002", "Ana Ruiz",
                List.of(new LineaDetalle("68f1a2b3c4d5e6f700000003", "Tinta para sello",
                        new BigDecimal("41.15"), 3, new BigDecimal("123.45"))),
                new BigDecimal("123.45"),
                "existencia insuficiente de Tinta para sello, solicitado 3",
                Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-10-31T21:00:04Z"));
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "VENDEDOR"})
    void devuelve200ConElDetalleCompleto(String rol) throws Exception {
        when(servicio.consultar(any(), any())).thenReturn(cancelada());

        mvc.perform(get("/ventas/{id}", ID).header("X-User-Id", "u").header("X-User-Role", rol))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(ID))
                .andExpect(jsonPath("$.folio").value(1042))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.clienteNombre").value("Ana Ruiz"))
                .andExpect(jsonPath("$.total").value(123.45))
                .andExpect(jsonPath("$.lineas.length()").value(1))
                .andExpect(jsonPath("$.lineas[0].nombreProducto").value("Tinta para sello"))
                .andExpect(jsonPath("$.lineas[0].cantidad").value(3))
                .andExpect(jsonPath("$.lineas[0].subtotal").value(123.45));

        var id = ArgumentCaptor.forClass(String.class);
        var usuario = ArgumentCaptor.forClass(UsuarioActual.class);
        verify(servicio).consultar(id.capture(), usuario.capture());
        assertThat(id.getValue()).isEqualTo(ID);
        assertThat(usuario.getValue()).isEqualTo(new UsuarioActual("u", rol));
    }

    /**
     * El motivo viaja ya redactado para el cajero, no como código del catálogo de eventos: la traducción
     * ocurre en StockEventoListener antes de persistirlo. Si esto cambiara, el cliente web tendría que
     * aprender a traducir tres códigos y la pantalla de caja dejaría de ser solo presentación.
     */
    @Test
    void elMotivoLlegaRedactadoNoComoCodigo() throws Exception {
        when(servicio.consultar(any(), any())).thenReturn(cancelada());

        mvc.perform(get("/ventas/{id}", ID).header("X-User-Id", "u").header("X-User-Role", "VENDEDOR"))
                .andExpect(jsonPath("$.motivo")
                        .value("existencia insuficiente de Tinta para sello, solicitado 3"));
    }

    /**
     * Sin no-store, el sondeo de la caja podría recibir la primera respuesta PENDIENTE desde la caché del
     * navegador indefinidamente y no enterarse nunca de que la venta se confirmó.
     */
    @Test
    void prohibeAlmacenarLaRespuestaEnCache() throws Exception {
        when(servicio.consultar(any(), any())).thenReturn(cancelada());

        mvc.perform(get("/ventas/{id}", ID).header("X-User-Id", "u").header("X-User-Role", "VENDEDOR"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    /** Los campos que no aplican viajan en null, no se omiten: la forma del objeto no cambia. */
    @Test
    void unaVentaPendienteTraeMotivoYResolucionEnNulo() throws Exception {
        when(servicio.consultar(any(), any())).thenReturn(new VentaDetalleResponse(
                ID, 1043L, EstadoVenta.PENDIENTE, null, null,
                List.of(new LineaDetalle("68f1a2b3c4d5e6f700000003", "Tinta para sello",
                        new BigDecimal("41.15"), 1, new BigDecimal("41.15"))),
                new BigDecimal("41.15"), null,
                Instant.parse("2026-10-31T21:00:00Z"), null));

        mvc.perform(get("/ventas/{id}", ID).header("X-User-Id", "u").header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                // Las claves están presentes aunque no apliquen; lo que es nulo es el valor. Si se
                // omitieran, estas aserciones fallarían con PathNotFoundException, que es justamente la
                // señal que se busca: el cliente no debería tener que comprobar la existencia de cada
                // campo antes de leerlo.
                .andExpect(jsonPath("$.motivo").value(nullValue()))
                .andExpect(jsonPath("$.fechaResolucion").value(nullValue()))
                .andExpect(jsonPath("$.clienteId").value(nullValue()))
                .andExpect(jsonPath("$.clienteNombre").value(nullValue()));
    }

    @Test
    void ventaInexistenteEs404ConProblemDetail() throws Exception {
        when(servicio.consultar(any(), any())).thenThrow(new VentaNoEncontradaException(ID));

        mvc.perform(get("/ventas/{id}", ID).header("X-User-Id", "u").header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Venta no encontrada"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void identificadorMalFormadoEs400() throws Exception {
        when(servicio.consultar(any(), any()))
                .thenThrow(new VentaInvalidaException("El formato del identificador de venta es inválido"));

        mvc.perform(get("/ventas/{id}", "no-es-un-objectid")
                        .header("X-User-Id", "u").header("X-User-Role", "VENDEDOR"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    /** Sin las cabeceras del Gateway la petición no se autenticó: 401, no 400. */
    @Test
    void sinCabecerasDelGatewayEs401() throws Exception {
        mvc.perform(get("/ventas/{id}", ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("No autenticado"));
        verifyNoInteractions(servicio);
    }

    @Test
    void rolSinPermisoEs403() throws Exception {
        when(servicio.consultar(eq(ID), any()))
                .thenThrow(new AccesoDenegadoException("Solo ADMIN o VENDEDOR pueden consultar ventas"));

        mvc.perform(get("/ventas/{id}", ID).header("X-User-Id", "u").header("X-User-Role", "INVITADO"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo ADMIN o VENDEDOR pueden consultar ventas"));
    }
}
