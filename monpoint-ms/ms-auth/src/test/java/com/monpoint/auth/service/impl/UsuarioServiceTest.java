package com.monpoint.auth.service.impl;

import com.monpoint.auth.audit.AuthAuditService;
import com.monpoint.auth.dto.request.ActualizarUsuarioRequest;
import com.monpoint.auth.dto.request.RegistroRequest;
import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.event.AuthEventPublisher;
import com.monpoint.auth.exception.CorreoDuplicadoException;
import com.monpoint.auth.exception.ModificacionRolPropioException;
import com.monpoint.auth.exception.OperacionInvalidaException;
import com.monpoint.auth.exception.PasswordValidationException;
import com.monpoint.auth.exception.UsuarioNoEncontradoException;
import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.model.UsuarioDePrueba;
import com.monpoint.auth.repository.UsuarioRepository;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.validator.PasswordValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Paso 7.2 — Reglas de negocio de usuarios (H-011, H-012, H-013).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    private static final UserPrincipal ADMIN = new UserPrincipal("admin-1", "admin@monpoint.com", Rol.ADMIN);
    private static final UserPrincipal VENDEDOR = new UserPrincipal("vend-1", "vale@monpoint.com", Rol.VENDEDOR);

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthAuditService auditoria;

    @Mock
    private AuthEventPublisher eventPublisher;

    private UsuarioServiceImpl usuarioService;

    @BeforeEach
    void setUp() {
        // PasswordValidator real: es lógica pura y así las pruebas ejercen la política verdadera.
        usuarioService = new UsuarioServiceImpl(
                usuarioRepository, passwordEncoder, new PasswordValidator(), auditoria, eventPublisher);
    }

    // ---------------------------------------------------------------- registrar

    @Test
    void registrar_ConCorreoNuevo_GuardaElHashYPublicaElEvento() {
        when(usuarioRepository.existsByCorreo("vale@monpoint.com")).thenReturn(false);
        when(passwordEncoder.encode("Secreto1!")).thenReturn("hash-bcrypt");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> {
            Usuario guardado = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardado, "id", "nuevo-1");
            return guardado;
        });

        UsuarioResponse respuesta = usuarioService.registrar(
                new RegistroRequest("Vale", " Vale@MonPoint.com ", "Secreto1!", Rol.VENDEDOR), ADMIN);

        assertThat(respuesta).isEqualTo(new UsuarioResponse("nuevo-1", "Vale", "vale@monpoint.com", "VENDEDOR", true));
        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("hash-bcrypt");
        verify(eventPublisher).publicarUsuarioCreado(guardado.getValue());
        verify(auditoria).usuarioCreado(guardado.getValue(), ADMIN);
    }

    @Test
    void registrar_ConCorreoDuplicado_LanzaCorreoDuplicadoSinGuardar() {
        when(usuarioRepository.existsByCorreo("vale@monpoint.com")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.registrar(
                new RegistroRequest("Vale", "vale@monpoint.com", "Secreto1!", Rol.VENDEDOR), ADMIN))
                .isInstanceOf(CorreoDuplicadoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void registrar_ConPasswordDebil_LanzaPasswordValidationSinTocarLaBD() {
        assertThatThrownBy(() -> usuarioService.registrar(
                new RegistroRequest("Vale", "vale@monpoint.com", "abc", Rol.VENDEDOR), ADMIN))
                .isInstanceOf(PasswordValidationException.class);
        verifyNoInteractions(usuarioRepository);
    }

    // ---------------------------------------------------------------- modificar

    @Test
    void modificar_PropioPerfilConElMismoCorreo_NoConsultaDuplicadosNiLanza409() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        UsuarioResponse respuesta = usuarioService.modificar("vend-1",
                new ActualizarUsuarioRequest("Valeria", "vale@monpoint.com", null, null), VENDEDOR);

        assertThat(respuesta.nombre()).isEqualTo("Valeria");
        verify(usuarioRepository, never()).existsByCorreoAndIdNot(any(), any());
    }

    @Test
    void modificar_ConElCorreoDeOtroUsuario_LanzaCorreoDuplicado() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(usuarioRepository.existsByCorreoAndIdNot("admin@monpoint.com", "vend-1")).thenReturn(true);

        assertThatThrownBy(() -> usuarioService.modificar("vend-1",
                new ActualizarUsuarioRequest("Vale", "admin@monpoint.com", null, null), VENDEDOR))
                .isInstanceOf(CorreoDuplicadoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void modificar_VendedorAOtroUsuario_LanzaAccessDeniedSinBuscarlo() {
        assertThatThrownBy(() -> usuarioService.modificar("admin-1",
                new ActualizarUsuarioRequest("X", "admin@monpoint.com", null, null), VENDEDOR))
                .isInstanceOf(AccessDeniedException.class);
        // 403 antes que 404: un VENDEDOR no puede sondear qué ids existen.
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void modificar_VendedorIntentaCambiarSuRol_LanzaModificacionRolPropio() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));

        assertThatThrownBy(() -> usuarioService.modificar("vend-1",
                new ActualizarUsuarioRequest("Vale", "vale@monpoint.com", null, Rol.ADMIN), VENDEDOR))
                .isInstanceOf(ModificacionRolPropioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void modificar_AdminIntentaCambiarSuPropioRol_LanzaModificacionRolPropio() {
        when(usuarioRepository.findById("admin-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("admin-1", "admin@monpoint.com", Rol.ADMIN)));

        assertThatThrownBy(() -> usuarioService.modificar("admin-1",
                new ActualizarUsuarioRequest("Admin", "admin@monpoint.com", null, Rol.VENDEDOR), ADMIN))
                .isInstanceOf(ModificacionRolPropioException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void modificar_AdminCambiaElRolDeOtroUsuario_LoAsciende() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        UsuarioResponse respuesta = usuarioService.modificar("vend-1",
                new ActualizarUsuarioRequest("Vale", "vale@monpoint.com", null, Rol.ADMIN), ADMIN);

        assertThat(respuesta.rol()).isEqualTo("ADMIN");
        verify(auditoria).usuarioModificado(any(Usuario.class), eq(ADMIN), eq(Rol.VENDEDOR), eq(false));
    }

    @Test
    void modificar_ConPasswordNueva_LaValidaYLaGuardaCifrada() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(passwordEncoder.encode("NuevaClave1!")).thenReturn("hash-nuevo");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        usuarioService.modificar("vend-1",
                new ActualizarUsuarioRequest("Vale", "vale@monpoint.com", "NuevaClave1!", null), VENDEDOR);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo("hash-nuevo");
    }

    @Test
    void modificar_ConPasswordVacia_ConservaLaActual() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        usuarioService.modificar("vend-1", new ActualizarUsuarioRequest("Vale", "vale@monpoint.com", "", null), VENDEDOR);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().getPasswordHash()).isEqualTo(UsuarioDePrueba.HASH);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void modificar_ConPasswordNuevaDebil_LanzaPasswordValidationSinGuardar() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));

        assertThatThrownBy(() -> usuarioService.modificar("vend-1",
                new ActualizarUsuarioRequest("Vale", "vale@monpoint.com", "corta", null), VENDEDOR))
                .isInstanceOf(PasswordValidationException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void modificar_IdInexistente_LanzaUsuarioNoEncontrado() {
        when(usuarioRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.modificar("no-existe",
                new ActualizarUsuarioRequest("X", "x@monpoint.com", null, null), ADMIN))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    // ---------------------------------------------------------------- desactivar, listar y consultar

    @Test
    void desactivar_AdminASiMismo_LanzaOperacionInvalidaSinTocarLaBD() {
        assertThatThrownBy(() -> usuarioService.desactivar("admin-1", ADMIN))
                .isInstanceOf(OperacionInvalidaException.class)
                .hasMessage("El administrador no puede desactivar su propia cuenta");
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void desactivar_OtroUsuario_LoGuardaInactivo() {
        when(usuarioRepository.findById("vend-1"))
                .thenReturn(Optional.of(UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));

        usuarioService.desactivar("vend-1", ADMIN);

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertThat(guardado.getValue().isActivo()).isFalse();
    }

    @Test
    void listarActivos_ConvierteCadaUsuarioEnSuRespuesta() {
        when(usuarioRepository.findByActivoTrue()).thenReturn(List.of(
                UsuarioDePrueba.conId("admin-1", "admin@monpoint.com", Rol.ADMIN),
                UsuarioDePrueba.conId("vend-1", "vale@monpoint.com", Rol.VENDEDOR)));

        assertThat(usuarioService.listarActivos())
                .extracting(UsuarioResponse::correo, UsuarioResponse::rol)
                .containsExactly(
                        tuple("admin@monpoint.com", "ADMIN"),
                        tuple("vale@monpoint.com", "VENDEDOR"));
    }

    @Test
    void obtenerPorId_Inexistente_LanzaUsuarioNoEncontrado() {
        when(usuarioRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> usuarioService.obtenerPorId("no-existe"))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }
}
