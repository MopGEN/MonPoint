package com.monpoint.reportes.controller;

import com.monpoint.reportes.config.SecurityConfig;
import com.monpoint.reportes.dto.response.DocumentoPdf;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.exception.VentaCanceladaException;
import com.monpoint.reportes.exception.VentaNoDisponibleException;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.DocumentoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentoController.class)
@Import(SecurityConfig.class)
class DocumentoControllerTest {

    private static final String ID_USUARIO = "6abf387781233486cd272c8e";
    private static final byte[] PDF = "%PDF-1.7 prueba".getBytes();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentoService documentoService;

    // ------------------------------------------------------------------ ticket

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "VENDEDOR"})
    void ticket_AmbosRoles_RecibenElPdfParaAbrirEnElNavegador(String rol) throws Exception {
        when(documentoService.ticket("v1")).thenReturn(new DocumentoPdf("ticket-folio-42.pdf", PDF));

        mockMvc.perform(conIdentidad(rol, "/reportes/ventas/v1/ticket"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"ticket-folio-42.pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void ticket_NoDisponible_Responde404ConCodigoParaReintentar() throws Exception {
        when(documentoService.ticket("v1")).thenThrow(new VentaNoDisponibleException("todavía no está disponible"));

        mockMvc.perform(conIdentidad("VENDEDOR", "/reportes/ventas/v1/ticket"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Venta no disponible"))
                .andExpect(jsonPath("$.codigo").value("VENTA_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void ticket_Cancelada_Responde409() throws Exception {
        when(documentoService.ticket("v1")).thenThrow(new VentaCanceladaException("fue cancelada"));

        mockMvc.perform(conIdentidad("ADMIN", "/reportes/ventas/v1/ticket"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Venta cancelada"));
    }

    @Test
    void ticket_SinIdentidad_Responde401() throws Exception {
        mockMvc.perform(get("/reportes/ventas/v1/ticket"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(documentoService);
    }

    @Test
    void ticket_ClienteQueSoloAceptaPdf_RecibeElCodigoDeErrorCorrecto() throws Exception {
        when(documentoService.ticket("v1")).thenThrow(new VentaNoDisponibleException("todavía no está disponible"));

        mockMvc.perform(conIdentidad("ADMIN", "/reportes/ventas/v1/ticket").accept(MediaType.APPLICATION_PDF))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ historial

    @Test
    void historial_ComoAdmin_RecibeElPdf() throws Exception {
        when(documentoService.historial(any(), any(), any()))
                .thenReturn(new DocumentoPdf("historial-ventas-2026-11-01-a-2026-11-08.pdf", PDF));

        mockMvc.perform(conIdentidad("ADMIN", "/reportes/ventas/historial?desde=2026-11-01&hasta=2026-11-08"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "inline; filename=\"historial-ventas-2026-11-01-a-2026-11-08.pdf\""));
        verify(documentoService).historial(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8),
                new UsuarioActual(ID_USUARIO, Rol.ADMIN));
    }

    @Test
    void historial_ComoVendedor_Responde403() throws Exception {
        when(documentoService.historial(any(), any(), any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede exportar el historial de ventas"));

        mockMvc.perform(conIdentidad("VENDEDOR", "/reportes/ventas/historial?desde=2026-11-01&hasta=2026-11-08"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Acceso denegado"));
    }

    @Test
    void historial_RangoInvalido_Responde400() throws Exception {
        when(documentoService.historial(any(), any(), any()))
                .thenThrow(new RangoInvalidoException("La fecha 'hasta' no puede ser anterior a 'desde'"));

        mockMvc.perform(conIdentidad("ADMIN", "/reportes/ventas/historial?desde=2026-11-08&hasta=2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Rango inválido"));
    }

    @Test
    void historial_SinHasta_Responde400() throws Exception {
        mockMvc.perform(conIdentidad("ADMIN", "/reportes/ventas/historial?desde=2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el parámetro 'hasta'"));
        verifyNoInteractions(documentoService);
    }

    @Test
    void historial_FechaMalFormada_Responde400() throws Exception {
        mockMvc.perform(conIdentidad("ADMIN", "/reportes/ventas/historial?desde=1-11-2026&hasta=2026-11-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Parámetro inválido"));
        verifyNoInteractions(documentoService);
    }

    // ------------------------------------------------------------------ listados

    @Test
    void listadoProductos_ComoAdmin_RecibeElPdf() throws Exception {
        when(documentoService.listadoProductos(any()))
                .thenReturn(new DocumentoPdf("listado-productos-2026-11-08.pdf", PDF));

        mockMvc.perform(conIdentidad("ADMIN", "/reportes/productos/listado"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(PDF));
        verify(documentoService).listadoProductos(new UsuarioActual(ID_USUARIO, Rol.ADMIN));
    }

    @Test
    void listadoClientes_ComoVendedor_Responde403() throws Exception {
        when(documentoService.listadoClientes(any()))
                .thenThrow(new AccesoDenegadoException("Solo un ADMIN puede exportar el listado de clientes"));

        mockMvc.perform(conIdentidad("VENDEDOR", "/reportes/clientes/listado"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listadoClientes_RolDesconocido_Responde400() throws Exception {
        mockMvc.perform(conIdentidad("OTRO", "/reportes/clientes/listado"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(documentoService);
    }

    private static MockHttpServletRequestBuilder conIdentidad(String rol, String ruta) {
        return get(ruta).header("X-User-Id", ID_USUARIO).header("X-User-Role", rol);
    }
}
