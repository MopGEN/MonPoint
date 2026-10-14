package com.monpoint.clientes.service;

import com.monpoint.clientes.dto.request.ClienteRequest;
import com.monpoint.clientes.dto.response.ClienteResponse;
import com.monpoint.clientes.event.ClienteEventPublisher;
import com.monpoint.clientes.exception.AccesoDenegadoException;
import com.monpoint.clientes.exception.RecursoNoEncontradoException;
import com.monpoint.clientes.model.Cliente;
import com.monpoint.clientes.model.Rol;
import com.monpoint.clientes.repository.ClienteRepository;
import com.monpoint.clientes.security.UsuarioActual;
import com.monpoint.clientes.service.impl.ClienteServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    private static final UsuarioActual ADMIN = new UsuarioActual("admin-1", Rol.ADMIN);
    private static final UsuarioActual VENDEDOR = new UsuarioActual("vend-1", Rol.VENDEDOR);

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private ClienteEventPublisher eventPublisher;

    @InjectMocks
    private ClienteServiceImpl service;

    @Test
    void crear_GuardaElClienteYPublicaClienteCreado() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteResponse respuesta = service.crear(
                new ClienteRequest("  Ana Pérez ", "ana@correo.com", "331", "Calle 1"), VENDEDOR);

        assertEquals("Ana Pérez", respuesta.nombre());
        verify(eventPublisher).publicarClienteCreado(any(Cliente.class));
        verify(eventPublisher, never()).publicarClienteActualizado(any());
    }

    @Test
    void listar_SinNombre_DevuelveTodos() {
        when(clienteRepository.findAll()).thenReturn(List.of(Cliente.crear("Ana", null, null, null)));

        assertEquals(1, service.listar(null).size());
        assertEquals(1, service.listar("   ").size());
    }

    @Test
    void listar_ConNombre_BuscaPorNombreParcial() {
        when(clienteRepository.findByNombreContainingIgnoreCase("ana"))
                .thenReturn(List.of(Cliente.crear("Ana", null, null, null)));

        assertEquals(1, service.listar(" ana ").size());
        verify(clienteRepository, never()).findAll();
    }

    @Test
    void obtener_Inexistente_LanzaRecursoNoEncontrado() {
        when(clienteRepository.findById("x")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.obtener("x"));
    }

    @Test
    void modificar_ActualizaYPublicaClienteActualizado() {
        Cliente existente = Cliente.crear("Ana", "ana@correo.com", null, null);
        when(clienteRepository.findById("1")).thenReturn(Optional.of(existente));
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));

        ClienteResponse respuesta = service.modificar("1",
                new ClienteRequest("Ana María", "nueva@correo.com", "331", "Calle 2"), VENDEDOR);

        assertEquals("Ana María", respuesta.nombre());
        assertEquals("nueva@correo.com", respuesta.correo());
        verify(eventPublisher).publicarClienteActualizado(existente);
    }

    @Test
    void modificar_Inexistente_LanzaRecursoNoEncontradoYNoPublica() {
        when(clienteRepository.findById("x")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.modificar("x", new ClienteRequest("Ana", null, null, null), ADMIN));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void eliminar_ComoVendedor_LanzaAccesoDenegadoSinTocarLaBase() {
        assertThrows(AccesoDenegadoException.class, () -> service.eliminar("1", VENDEDOR));

        verifyNoInteractions(clienteRepository);
    }

    @Test
    void eliminar_ComoAdmin_BorraElCliente() {
        Cliente existente = Cliente.crear("Ana", null, null, null);
        when(clienteRepository.findById("1")).thenReturn(Optional.of(existente));

        service.eliminar("1", ADMIN);

        verify(clienteRepository).delete(existente);
    }

    @Test
    void eliminar_Inexistente_LanzaRecursoNoEncontrado() {
        when(clienteRepository.findById("x")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminar("x", ADMIN));
    }
}
