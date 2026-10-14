package com.monpoint.auth.service.impl;

import com.monpoint.auth.audit.AuthAuditService;
import com.monpoint.auth.dto.request.ActualizarUsuarioRequest;
import com.monpoint.auth.dto.request.RegistroRequest;
import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.event.AuthEventPublisher;
import com.monpoint.auth.exception.CorreoDuplicadoException;
import com.monpoint.auth.exception.ModificacionRolPropioException;
import com.monpoint.auth.exception.OperacionInvalidaException;
import com.monpoint.auth.exception.UsuarioNoEncontradoException;
import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.repository.UsuarioRepository;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.service.UsuarioService;
import com.monpoint.auth.validator.PasswordValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reglas de negocio de usuarios. Qué rol entra a cada ruta lo decide {@code SecurityConfig};
 * aquí se decide quién puede modificar a quién y qué (H-013).
 */
@Service
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final AuthAuditService auditoria;
    private final AuthEventPublisher eventPublisher;

    @Override
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request, UserPrincipal ejecutor) {
        passwordValidator.validar(request.password());
        if (usuarioRepository.existsByCorreo(request.correo())) {
            throw new CorreoDuplicadoException(request.correo());
        }

        Usuario usuario = usuarioRepository.save(Usuario.create(
                request.correo(), passwordEncoder.encode(request.password()), request.nombre(), request.rol()));

        auditoria.usuarioCreado(usuario, ejecutor);
        eventPublisher.publicarUsuarioCreado(usuario);
        return UsuarioResponse.from(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse modificar(String id, ActualizarUsuarioRequest request, UserPrincipal ejecutor) {
        boolean esPropioPerfil = id.equals(ejecutor.id());
        // Se revisa antes de buscar: así un VENDEDOR recibe 403 y no puede sondear qué ids existen (404).
        if (ejecutor.rol() != Rol.ADMIN && !esPropioPerfil) {
            throw new AccessDeniedException("Solo puedes modificar tu propio perfil");
        }

        Usuario usuario = buscarPorId(id);
        Rol rolAnterior = usuario.getRol();
        boolean cambiaRol = request.rol() != null && request.rol() != rolAnterior;
        if (cambiaRol && esPropioPerfil) {
            throw new ModificacionRolPropioException();
        }
        // Anti-falso 409: solo se consulta la BD si el correo realmente cambió.
        if (!usuario.getCorreo().equalsIgnoreCase(request.correo())
                && usuarioRepository.existsByCorreoAndIdNot(request.correo(), id)) {
            throw new CorreoDuplicadoException(request.correo());
        }
        if (request.cambiaPassword()) {
            passwordValidator.validar(request.password());
        }

        // Todas las reglas pasaron: recién ahora se modifica la entidad.
        usuario.actualizarDatos(request.nombre(), request.correo());
        if (cambiaRol) {
            usuario.cambiarRol(request.rol());
        }
        if (request.cambiaPassword()) {
            usuario.cambiarPassword(passwordEncoder.encode(request.password()));
        }

        Usuario guardado = usuarioRepository.save(usuario);
        auditoria.usuarioModificado(guardado, ejecutor, rolAnterior, request.cambiaPassword());
        return UsuarioResponse.from(guardado);
    }

    @Override
    public List<UsuarioResponse> listarActivos() {
        return usuarioRepository.findByActivoTrue().stream()
                .map(UsuarioResponse::from)
                .toList();
    }

    @Override
    public UsuarioResponse obtenerPorId(String id) {
        return UsuarioResponse.from(buscarPorId(id));
    }

    @Override
    @Transactional
    public void desactivar(String id, UserPrincipal ejecutor) {
        if (id.equals(ejecutor.id())) {
            throw new OperacionInvalidaException("El administrador no puede desactivar su propia cuenta");
        }

        Usuario usuario = buscarPorId(id);
        usuario.desactivar();
        usuarioRepository.save(usuario);
        auditoria.usuarioDesactivado(usuario, ejecutor);
    }

    private Usuario buscarPorId(String id) {
        return usuarioRepository.findById(id).orElseThrow(() -> new UsuarioNoEncontradoException(id));
    }
}
