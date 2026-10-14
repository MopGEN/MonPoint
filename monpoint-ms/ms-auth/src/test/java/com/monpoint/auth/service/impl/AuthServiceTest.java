package com.monpoint.auth.service.impl;

import com.monpoint.auth.audit.AuthAuditService;
import com.monpoint.auth.audit.AuthAuditService.MotivoLoginFallido;
import com.monpoint.auth.dto.request.LoginRequest;
import com.monpoint.auth.dto.response.AuthResponse;
import com.monpoint.auth.exception.CredencialesInvalidasException;
import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.model.UsuarioDePrueba;
import com.monpoint.auth.repository.UsuarioRepository;
import com.monpoint.auth.security.JwtService;
import com.monpoint.auth.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Paso 7.2 — Login (H-010): token con el rol correcto y el mismo 401 opaco para toda falla.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthAuditService auditoria;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_ConCredencialesValidas_DevuelveTokenConElRolDelUsuario() {
        Usuario vale = UsuarioDePrueba.conId("u-1", "vale@monpoint.com", Rol.VENDEDOR);
        when(usuarioRepository.findByCorreo("vale@monpoint.com")).thenReturn(Optional.of(vale));
        when(passwordEncoder.matches("Secreto1!", UsuarioDePrueba.HASH)).thenReturn(true);
        when(jwtService.generarToken(new UserPrincipal("u-1", "vale@monpoint.com", Rol.VENDEDOR)))
                .thenReturn("jwt.firmado");
        when(jwtService.getExpiracionMs()).thenReturn(86_400_000L);

        // El correo llega con mayúsculas: el DTO lo normaliza antes de buscarlo.
        AuthResponse respuesta = authService.login(new LoginRequest("Vale@MonPoint.com", "Secreto1!"));

        assertThat(respuesta.token()).isEqualTo("jwt.firmado");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.rol()).isEqualTo("VENDEDOR");
        assertThat(respuesta.expiraEn()).isEqualTo(86_400_000L);
        verify(auditoria).loginExitoso(vale);
    }

    @Test
    void login_ConPasswordIncorrecta_LanzaCredencialesInvalidasSinEmitirToken() {
        when(usuarioRepository.findByCorreo("vale@monpoint.com"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("u-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(passwordEncoder.matches("Mala123!", UsuarioDePrueba.HASH)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("vale@monpoint.com", "Mala123!")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales incorrectas");
        verify(jwtService, never()).generarToken(any());
        verify(auditoria).loginFallido("vale@monpoint.com", MotivoLoginFallido.PASSWORD_INCORRECTA);
    }

    @Test
    void login_ConUsuarioInactivo_LanzaElMismoErrorOpaco() {
        when(usuarioRepository.findByCorreo("vale@monpoint.com"))
                .thenReturn(Optional.of(UsuarioDePrueba.inactivo("u-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(passwordEncoder.matches("Secreto1!", UsuarioDePrueba.HASH)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("vale@monpoint.com", "Secreto1!")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales incorrectas");
        verify(jwtService, never()).generarToken(any());
        verify(auditoria).loginFallido("vale@monpoint.com", MotivoLoginFallido.USUARIO_INACTIVO);
    }

    @Test
    void login_ConCorreoInexistente_LanzaElMismoErrorOpacoYPagaUnBCryptIgual() {
        when(usuarioRepository.findByCorreo("nadie@monpoint.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-ficticio");

        assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@monpoint.com", "Mala123!")))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Credenciales incorrectas");
        // Se compara contra un hash ficticio para que el tiempo de respuesta no delate que el correo no existe.
        verify(passwordEncoder).matches("Mala123!", "hash-ficticio");
        verify(auditoria).loginFallido("nadie@monpoint.com", MotivoLoginFallido.USUARIO_NO_ENCONTRADO);
    }
}
