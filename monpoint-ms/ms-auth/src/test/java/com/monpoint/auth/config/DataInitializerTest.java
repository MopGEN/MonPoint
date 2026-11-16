package com.monpoint.auth.config;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.repository.UsuarioRepository;
import com.monpoint.auth.validator.PasswordValidator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    // Claves del doble de prueba; nunca valores por omisión del servicio.
    private static final String[] VARIABLES = {"AUTOTACHI_VICTOR_PASSWORD", "AUTOTACHI_ALEJANDRO_PASSWORD",
            "AUTOTACHI_HUGO_PASSWORD", "AUTOTACHI_VENDEDOR_PASSWORD"};
    private static final List<String> CORREOS = List.of("victor.ortiz@autotachi.mx", "alejandro.bermudez@autotachi.mx",
            "hugo.trejo@autotachi.mx", "vendedor@autotachi.mx");
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class)
            .withInitializer(ctx -> ctx.getEnvironment().getPropertySources()
                    .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME));

    private ApplicationContextRunner habilitado() {
        return contexto.withPropertyValues("SEMILLA_NOMINAL_HABILITADA=true",
                VARIABLES[0] + "=  VictorPrueba1!  ", VARIABLES[1] + "=AlejandroPrueba2!",
                VARIABLES[2] + "=HugoPrueba3!", VARIABLES[3] + "=VendedorPrueba4!");
    }

    @Test
    void creaCuatroCuentasConRolesAcordadosYClavesCifradasSinRecortar() {
        habilitado().run(ctx -> {
            ctx.getBean(DataInitializer.class).run();
            ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
            verify(ctx.getBean(UsuarioRepository.class), times(4)).save(captura.capture());
            List<Usuario> creados = captura.getAllValues();
            assertThat(creados).extracting(Usuario::getCorreo).containsExactlyElementsOf(CORREOS);
            assertThat(creados).extracting(Usuario::getRol).containsExactly(Rol.ADMIN, Rol.ADMIN, Rol.ADMIN, Rol.VENDEDOR);
            assertThat(creados).extracting(Usuario::getNombre).containsExactly("Víctor Ortiz", "Alejandro Bermúdez", "Hugo Trejo", "Vendedor");
            for (int i = 0; i < creados.size(); i++) {
                String password = ctx.getEnvironment().getProperty(VARIABLES[i]);
                assertThat(creados.get(i).getPasswordHash()).isNotEqualTo(password);
                assertThat(ctx.getBean(PasswordEncoder.class).matches(password, creados.get(i).getPasswordHash())).isTrue();
            }
        });
    }

    @Test
    void losEspaciosDeUnaClaveDeEntornoNoSeRecortan() {
        String password = "  VictorPrueba1!  ";
        habilitado().withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("clave-con-espacios", Map.of(VARIABLES[0], password)))).run(ctx -> {
            ctx.getBean(DataInitializer.class).run();
            ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
            verify(ctx.getBean(UsuarioRepository.class), times(4)).save(captura.capture());
            String hash = captura.getAllValues().getFirst().getPasswordHash();
            assertThat(ctx.getBean(PasswordEncoder.class).matches(password, hash)).isTrue();
            assertThat(ctx.getBean(PasswordEncoder.class).matches(password.trim(), hash)).isFalse();
        });
    }

    @Test
    void baseVaciaSinActivacionExplicitaFallaSinCrearUnaCuentaGenerica() {
        contexto.run(ctx -> {
            assertThatIllegalStateException().isThrownBy(() -> ctx.getBean(DataInitializer.class).run())
                    .withMessageContaining("No hay contraseña por omisión");
            verify(ctx.getBean(UsuarioRepository.class), never()).save(any());
        });
    }

    @Test
    void despliegueExistenteSinActivacionNoExigeClavesNiCambiaCuentas() {
        contexto.run(ctx -> {
            UsuarioRepository repo = ctx.getBean(UsuarioRepository.class);
            when(repo.count()).thenReturn(2L);
            ctx.getBean(DataInitializer.class).run();
            verify(repo, never()).save(any());
            verify(repo, never()).existsByCorreo(any());
        });
    }

    @Test
    void claveAusenteVaciaInvalidaOConMasDe72BytesImpideTodasLasAltas() {
        for (String password : List.of("", "invalida", "Á".repeat(35) + "a1!")) {
            habilitado().withPropertyValues(VARIABLES[3] + "=" + password).run(ctx -> {
                assertThatIllegalStateException().isThrownBy(() -> ctx.getBean(DataInitializer.class).run())
                        .withMessageContaining(VARIABLES[3]).withMessageNotContaining("invalida");
                verify(ctx.getBean(UsuarioRepository.class), never()).save(any());
            });
        }
        contexto.withPropertyValues("SEMILLA_NOMINAL_HABILITADA=true").run(ctx -> {
            assertThatIllegalStateException().isThrownBy(() -> ctx.getBean(DataInitializer.class).run())
                    .withMessageContaining(VARIABLES[0]);
            verify(ctx.getBean(UsuarioRepository.class), never()).save(any());
        });
    }

    @Test
    void correosExistentesInclusoInactivosNoSeGuardanNiExigenLaClaveAnterior() {
        contexto.withPropertyValues("SEMILLA_NOMINAL_HABILITADA=true").run(ctx -> {
            UsuarioRepository repo = ctx.getBean(UsuarioRepository.class);
            when(repo.existsByCorreo(any())).thenReturn(true);
            ctx.getBean(DataInitializer.class).run();
            verify(repo, never()).save(any());
            verify(repo, never()).findByActivoTrue();
        });
    }

    @Test
    void completaSoloLaCuentaAusenteAunqueHayaUsuariosGenericos() {
        contexto.withPropertyValues("SEMILLA_NOMINAL_HABILITADA=true", VARIABLES[3] + "=VendedorPrueba4!").run(ctx -> {
            UsuarioRepository repo = ctx.getBean(UsuarioRepository.class);
            when(repo.count()).thenReturn(10L);
            when(repo.existsByCorreo(any())).thenAnswer(i -> !i.getArgument(0).equals(CORREOS.getLast()));
            ctx.getBean(DataInitializer.class).run();
            ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
            verify(repo).save(captura.capture());
            assertThat(captura.getValue().getCorreo()).isEqualTo(CORREOS.getLast());
        });
    }

    @Test
    void indiceUnicoResuelveCarreraPeroNoOcultaOtrosConflictos() {
        habilitado().run(ctx -> {
            UsuarioRepository repo = ctx.getBean(UsuarioRepository.class);
            when(repo.existsByCorreo(any())).thenReturn(true);
            when(repo.existsByCorreo(CORREOS.getFirst())).thenReturn(false, true);
            when(repo.save(any())).thenThrow(new DuplicateKeyException("carrera"));
            assertThatCode(() -> ctx.getBean(DataInitializer.class).run()).doesNotThrowAnyException();
        });
        habilitado().run(ctx -> {
            UsuarioRepository repo = ctx.getBean(UsuarioRepository.class);
            when(repo.save(any())).thenThrow(new DuplicateKeyException("otro índice"));
            assertThatExceptionOfType(DuplicateKeyException.class).isThrownBy(() -> ctx.getBean(DataInitializer.class).run());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DataInitializer.class, PasswordValidator.class})
    static class Configuracion {
        @Bean UsuarioRepository usuarios() { return mock(UsuarioRepository.class); }
        @Bean PasswordEncoder encoder() { return new BCryptPasswordEncoder(4); }
    }
}
