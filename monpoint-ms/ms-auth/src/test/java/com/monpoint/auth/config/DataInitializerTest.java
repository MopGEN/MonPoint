package com.monpoint.auth.config;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DataInitializerTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(Configuracion.class);

    @Test
    void variableAusenteCreaAdministradorConClavePorOmisionCifrada() {
        comprobarAlta(contexto, "Admin123!");
    }

    @Test
    void variableVaciaUsaLaMismaClavePorOmisionQueCompose() {
        comprobarAlta(contexto.withPropertyValues("ADMIN_DEFAULT_PASSWORD="), "Admin123!");
    }

    @Test
    void clavePersonalizadaSeConserva() {
        comprobarAlta(contexto.withPropertyValues("ADMIN_DEFAULT_PASSWORD=OtraClave31!"), "OtraClave31!");
    }

    @Test
    void baseConUsuariosNoCambiaNingunaClaveAunqueCambieLaConfiguracion() {
        contexto.withPropertyValues("ADMIN_DEFAULT_PASSWORD=").run(ctx -> {
            UsuarioRepository usuarios = ctx.getBean(UsuarioRepository.class);
            when(usuarios.count()).thenReturn(1L);
            ctx.getBean(DataInitializer.class).run();
            verify(usuarios, never()).save(any());
        });
    }

    private void comprobarAlta(ApplicationContextRunner contexto, String esperada) {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            ctx.getBean(DataInitializer.class).run();
            ArgumentCaptor<Usuario> captura = ArgumentCaptor.forClass(Usuario.class);
            verify(ctx.getBean(UsuarioRepository.class)).save(captura.capture());
            Usuario usuario = captura.getValue();
            assertThat(usuario.getCorreo()).isEqualTo("admin@monpoint.com");
            assertThat(usuario.getRol()).isEqualTo(Rol.ADMIN);
            assertThat(usuario.getPasswordHash()).isNotEqualTo(esperada);
            assertThat(ctx.getBean(PasswordEncoder.class).matches(esperada, usuario.getPasswordHash())).isTrue();
            assertThat(ctx.getBean(PasswordEncoder.class).matches("", usuario.getPasswordHash())).isFalse();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DataInitializer.class)
    static class Configuracion {
        @Bean
        static PropertySourcesPlaceholderConfigurer propiedades() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        UsuarioRepository usuarios() {
            return mock(UsuarioRepository.class);
        }

        @Bean
        PasswordEncoder encoder() {
            return new BCryptPasswordEncoder(4);
        }
    }
}
