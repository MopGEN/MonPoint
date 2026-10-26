package com.monpoint.ventas.controller;

import com.monpoint.ventas.config.SecurityConfig;
import com.monpoint.ventas.event.VentaEventPublisher;
import com.monpoint.ventas.model.ProductoReplica;
import com.monpoint.ventas.repository.ClienteReplicaRepository;
import com.monpoint.ventas.repository.ProductoReplicaRepository;
import com.monpoint.ventas.repository.VentaRepository;
import com.monpoint.ventas.service.FolioService;
import com.monpoint.ventas.service.VentaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VentaController.class)
@Import({SecurityConfig.class, VentaService.class})
class VentaIdentificadoresTest {
    private static final String PRODUCTO_ID = "507f1f77bcf86cd799439011";
    private static final String CLIENTE_ID = "507f1f77bcf86cd799439012";
    @Autowired MockMvc mvc;
    @MockitoBean ProductoReplicaRepository productos;
    @MockitoBean ClienteReplicaRepository clientes;
    @MockitoBean VentaRepository ventas;
    @MockitoBean FolioService folios;
    @MockitoBean VentaEventPublisher publicador;
    @MockitoBean PlatformTransactionManager transacciones;

    @ParameterizedTest
    @ValueSource(strings = {"abc", "507f1f77bcf86cd79943901", "507f1f77bcf86cd7994390111",
            "507f1f77bcf86cd79943901z", " 507f1f77bcf86cd799439011"})
    void formatoInvalidoEs400AntesDeAbrirTransaccion(String id) throws Exception {
        comprobarFormato(id, PRODUCTO_ID, "clienteId");
        comprobarFormato(null, id, "productoId");
        verifyNoInteractions(productos, clientes, ventas, folios, publicador, transacciones);
    }

    @ParameterizedTest @ValueSource(strings = {"", " "})
    void clienteVacioNoEquivaleAClienteOmitido(String id) throws Exception {
        comprobarFormato(id, PRODUCTO_ID, "clienteId");
        verifyNoInteractions(productos, clientes, ventas, folios, publicador, transacciones);
    }

    @Test void clienteValidoPeroAusenteConservaMensajeDeConsistenciaEventual() throws Exception {
        when(productos.findById(PRODUCTO_ID)).thenReturn(Optional.of(
                ProductoReplica.crear(PRODUCTO_ID, "Tinta", BigDecimal.TEN, true, Instant.now())));
        when(clientes.findById(CLIENTE_ID)).thenReturn(Optional.empty());
        mvc.perform(post("/ventas").header("X-User-Id", "u").header("X-User-Role", "VENDEDOR")
                .contentType(MediaType.APPLICATION_JSON).content(solicitud(CLIENTE_ID, PRODUCTO_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(VentaService.CLIENTE_NO_DISPONIBLE));
        verify(clientes).findById(CLIENTE_ID);
        verifyNoInteractions(ventas, folios, publicador);
    }

    private void comprobarFormato(String clienteId, String productoId, String campo) throws Exception {
        mvc.perform(post("/ventas").header("X-User-Id", "u").header("X-User-Role", "VENDEDOR")
                .contentType(MediaType.APPLICATION_JSON).content(solicitud(clienteId, productoId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El formato de " + campo
                        + " es inválido: debe contener 24 caracteres hexadecimales"));
    }

    private String solicitud(String clienteId, String productoId) {
        return """
                {"clienteId":%s,"lineas":[{"productoId":"%s","cantidad":1}]}
                """.formatted(clienteId == null ? "null" : "\"" + clienteId + "\"", productoId);
    }
}
