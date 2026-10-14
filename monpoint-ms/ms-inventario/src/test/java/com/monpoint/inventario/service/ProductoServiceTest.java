package com.monpoint.inventario.service;

import com.monpoint.inventario.dto.request.ProductoRequest;
import com.monpoint.inventario.dto.response.ProductoResponse;
import com.monpoint.inventario.event.InventarioEventPublisher;
import com.monpoint.inventario.exception.AccesoDenegadoException;
import com.monpoint.inventario.exception.RecursoNoEncontradoException;
import com.monpoint.inventario.model.Producto;
import com.monpoint.inventario.model.Rol;
import com.monpoint.inventario.repository.ProductoRepository;
import com.monpoint.inventario.security.UsuarioActual;
import com.monpoint.inventario.service.impl.ProductoServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    private static final UsuarioActual ADMIN = new UsuarioActual("admin-1", Rol.ADMIN);
    private static final UsuarioActual VENDEDOR = new UsuarioActual("vend-1", Rol.VENDEDOR);

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private InventarioEventPublisher eventPublisher;

    @InjectMocks
    private ProductoServiceImpl service;

    private static ProductoRequest request(Integer umbral) {
        return new ProductoRequest("  Aceite 1L ", null, new BigDecimal("45.50"), 10, umbral);
    }

    @Test
    void crear_ComoAdmin_GuardaActivoConUmbralPorOmisionYPublicaProductoCreado() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse respuesta = service.crear(request(null), ADMIN);

        assertEquals("Aceite 1L", respuesta.nombre());
        assertEquals(new BigDecimal("45.50"), respuesta.precio());
        assertEquals(10, respuesta.existencia());
        assertEquals(Producto.UMBRAL_POR_OMISION, respuesta.umbralStock());
        assertTrue(respuesta.activo());
        verify(eventPublisher).publicarProductoCreado(any(Producto.class));
    }

    @Test
    void crear_ConUmbralExplicito_LoRespeta() {
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(2, service.crear(request(2), ADMIN).umbralStock());
    }

    @Test
    void crear_ComoVendedor_LanzaAccesoDenegadoSinGuardarNiPublicar() {
        assertThrows(AccesoDenegadoException.class, () -> service.crear(request(null), VENDEDOR));

        verifyNoInteractions(productoRepository, eventPublisher);
    }

    @Test
    void listar_DevuelveSoloLosActivos() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(Producto.crear("A", null, BigDecimal.ONE, 1, null)));

        assertEquals(1, service.listar().size());
        verify(productoRepository, never()).findAll();
    }

    @Test
    void obtener_ProductoInactivo_LoDevuelveConSuEstado() {
        Producto inactivo = Producto.crear("A", null, BigDecimal.ONE, 1, null);
        inactivo.darDeBaja();
        when(productoRepository.findById("1")).thenReturn(Optional.of(inactivo));

        assertFalse(service.obtener("1").activo());
    }

    @Test
    void obtener_Inexistente_LanzaRecursoNoEncontrado() {
        when(productoRepository.findById("x")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.obtener("x"));
    }

    @Test
    void modificar_ComoAdmin_ActualizaYPublicaProductoActualizado() {
        Producto existente = Producto.crear("Aceite", null, new BigDecimal("40.00"), 10, 3);
        when(productoRepository.findById("1")).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductoResponse respuesta = service.modificar("1",
                new ProductoRequest("Aceite 1L", "Vegetal", new BigDecimal("45.50"), 25, null), ADMIN);

        assertEquals("Aceite 1L", respuesta.nombre());
        assertEquals(new BigDecimal("45.50"), respuesta.precio());
        assertEquals(25, respuesta.existencia());
        assertEquals(3, respuesta.umbralStock(), "umbral null conserva el actual");
        verify(eventPublisher).publicarProductoActualizado(existente);
    }

    @Test
    void modificar_ComoVendedor_LanzaAccesoDenegado() {
        assertThrows(AccesoDenegadoException.class, () -> service.modificar("1", request(null), VENDEDOR));

        verifyNoInteractions(productoRepository, eventPublisher);
    }

    @Test
    void modificar_Inexistente_LanzaRecursoNoEncontradoYNoPublica() {
        when(productoRepository.findById("x")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.modificar("x", request(null), ADMIN));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void darDeBaja_ComoAdmin_MarcaInactivoYPublicaProductoDadoDeBaja() {
        Producto existente = Producto.crear("Aceite", null, BigDecimal.TEN, 10, null);
        when(productoRepository.findById("1")).thenReturn(Optional.of(existente));
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        service.darDeBaja("1", ADMIN);

        assertFalse(existente.isActivo());
        verify(productoRepository).save(existente);
        verify(eventPublisher).publicarProductoDadoDeBaja(existente);
    }

    @Test
    void darDeBaja_ProductoYaInactivo_NoVuelveAPublicar() {
        Producto existente = Producto.crear("Aceite", null, BigDecimal.TEN, 10, null);
        existente.darDeBaja();
        when(productoRepository.findById("1")).thenReturn(Optional.of(existente));

        service.darDeBaja("1", ADMIN);

        verify(productoRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void darDeBaja_ComoVendedor_LanzaAccesoDenegado() {
        assertThrows(AccesoDenegadoException.class, () -> service.darDeBaja("1", VENDEDOR));

        verifyNoInteractions(productoRepository, eventPublisher);
    }

    @Test
    void darDeBaja_Inexistente_LanzaRecursoNoEncontrado() {
        when(productoRepository.findById("x")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.darDeBaja("x", ADMIN));
    }
}
