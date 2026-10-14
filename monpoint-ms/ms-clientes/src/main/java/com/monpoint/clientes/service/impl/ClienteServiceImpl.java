package com.monpoint.clientes.service.impl;

import com.monpoint.clientes.dto.request.ClienteRequest;
import com.monpoint.clientes.dto.response.ClienteResponse;
import com.monpoint.clientes.event.ClienteEventPublisher;
import com.monpoint.clientes.exception.AccesoDenegadoException;
import com.monpoint.clientes.exception.RecursoNoEncontradoException;
import com.monpoint.clientes.model.Cliente;
import com.monpoint.clientes.repository.ClienteRepository;
import com.monpoint.clientes.security.UsuarioActual;
import com.monpoint.clientes.service.ClienteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reglas de H-040: alta y consulta con ambos roles; la baja exige ADMIN (RNF-07: el rol se verifica aquí).
 * <p>
 * ⚠️ Documentación no definida: (1) qué rol puede modificar (se permiten ambos); (2) si la baja es lógica
 * o física. El Doc 04 no tiene campo {@code activo} en {@code clientes} y el esquema está cerrado, así que la
 * baja es FÍSICA. No existe evento de baja en el diagrama, por lo que ninguna réplica se entera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final ClienteEventPublisher eventPublisher;

    @Override
    @Transactional
    public ClienteResponse crear(ClienteRequest request, UsuarioActual ejecutor) {
        Cliente guardado = clienteRepository.save(
                Cliente.crear(request.nombre(), request.correo(), request.telefono(), request.direccion()));
        log.info("evento=CLIENTE_CREADO clienteId={} usuarioId={}", guardado.getId(), ejecutor.id());
        eventPublisher.publicarClienteCreado(guardado);
        return ClienteResponse.from(guardado);
    }

    @Override
    public List<ClienteResponse> listar(String nombre) {
        List<Cliente> clientes = (nombre == null || nombre.isBlank())
                ? clienteRepository.findAll()
                : clienteRepository.findByNombreContainingIgnoreCase(nombre.trim());
        return clientes.stream().map(ClienteResponse::from).toList();
    }

    @Override
    public ClienteResponse obtener(String id) {
        return ClienteResponse.from(buscar(id));
    }

    @Override
    @Transactional
    public ClienteResponse modificar(String id, ClienteRequest request, UsuarioActual ejecutor) {
        Cliente cliente = buscar(id);
        cliente.actualizarDatos(request.nombre(), request.correo(), request.telefono(), request.direccion());
        Cliente guardado = clienteRepository.save(cliente);
        log.info("evento=CLIENTE_ACTUALIZADO clienteId={} usuarioId={}", guardado.getId(), ejecutor.id());
        eventPublisher.publicarClienteActualizado(guardado);
        return ClienteResponse.from(guardado);
    }

    @Override
    @Transactional
    public void eliminar(String id, UsuarioActual ejecutor) {
        if (!ejecutor.esAdmin()) {
            throw new AccesoDenegadoException("Solo un ADMIN puede dar de baja clientes");
        }
        Cliente cliente = buscar(id);
        clienteRepository.delete(cliente);
        log.info("evento=CLIENTE_ELIMINADO clienteId={} usuarioId={}", id, ejecutor.id());
    }

    private Cliente buscar(String id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente con id " + id));
    }
}
