package com.monpoint.clientes.service;

import com.monpoint.clientes.dto.request.ClienteRequest;
import com.monpoint.clientes.dto.response.ClienteResponse;
import com.monpoint.clientes.security.UsuarioActual;

import java.util.List;

public interface ClienteService {

    ClienteResponse crear(ClienteRequest request, UsuarioActual ejecutor);

    /** @param nombre fragmento del nombre; {@code null} o vacío devuelve todos */
    List<ClienteResponse> listar(String nombre);

    ClienteResponse obtener(String id);

    ClienteResponse modificar(String id, ClienteRequest request, UsuarioActual ejecutor);

    /** Solo ADMIN (H-040). */
    void eliminar(String id, UsuarioActual ejecutor);
}
