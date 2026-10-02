package com.monpoint.auth.service.impl;

import com.monpoint.auth.audit.AuthAuditService;
import com.monpoint.auth.audit.AuthAuditService.MotivoLoginFallido;
import com.monpoint.auth.dto.request.LoginRequest;
import com.monpoint.auth.dto.response.AuthResponse;
import com.monpoint.auth.exception.CredencialesInvalidasException;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.repository.UsuarioRepository;
import com.monpoint.auth.security.JwtService;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Login manual contra MongoDB, sin {@code AuthenticationManager} (H-010).
 * <p>
 * Toda falla termina en la misma {@link CredencialesInvalidasException}; el motivo real solo
 * queda en la bitácora de auditoría.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthAuditService auditoria;

    /** Hash de una contraseña aleatoria que nadie conoce; se calcula la primera vez que se necesita. */
    private volatile String hashFicticio;

    @Override
    public AuthResponse login(LoginRequest request) {
        Optional<Usuario> encontrado = usuarioRepository.findByCorreo(request.correo());
        if (encontrado.isEmpty()) {
            // Se paga un BCrypt completo de todos modos: responder más rápido delataría qué correos existen.
            passwordEncoder.matches(request.password(), hashFicticio());
            throw rechazar(request.correo(), MotivoLoginFallido.USUARIO_NO_ENCONTRADO);
        }

        Usuario usuario = encontrado.get();
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw rechazar(request.correo(), MotivoLoginFallido.PASSWORD_INCORRECTA);
        }
        if (!usuario.isActivo()) {
            throw rechazar(request.correo(), MotivoLoginFallido.USUARIO_INACTIVO);
        }

        String token = jwtService.generarToken(UserPrincipal.from(usuario));
        auditoria.loginExitoso(usuario);
        return AuthResponse.from(usuario, token, jwtService.getExpiracionMs());
    }

    private CredencialesInvalidasException rechazar(String correo, MotivoLoginFallido motivo) {
        auditoria.loginFallido(correo, motivo);
        return new CredencialesInvalidasException();
    }

    private String hashFicticio() {
        String hash = hashFicticio;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            hashFicticio = hash;
        }
        return hash;
    }
}
