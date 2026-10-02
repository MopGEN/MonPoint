package com.monpoint.auth.service;

import com.monpoint.auth.dto.request.ActualizarUsuarioRequest;
import com.monpoint.auth.dto.request.RegistroRequest;
import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.security.UserPrincipal;

import java.util.List;

/**
 * Gestión de usuarios (H-011, H-012, H-013). El {@code ejecutor} es quien hace la petición,
 * tomado del token: con él se aplican las reglas de autorización y se firma la auditoría.
 */
public interface UsuarioService {

    UsuarioResponse registrar(RegistroRequest request, UserPrincipal ejecutor);

    UsuarioResponse modificar(String id, ActualizarUsuarioRequest request, UserPrincipal ejecutor);

    List<UsuarioResponse> listarActivos();

    void desactivar(String id, UserPrincipal ejecutor);
}
