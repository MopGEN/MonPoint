package com.monpoint.reportes.service;

import com.monpoint.reportes.dto.request.ConfiguracionRequest;
import com.monpoint.reportes.dto.response.ConfiguracionResponse;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RecursoNoEncontradoException;
import com.monpoint.reportes.model.Configuracion;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.repository.ConfiguracionRepository;
import com.monpoint.reportes.security.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-08T18:00:00Z");
    private static final UsuarioActual ADMIN = new UsuarioActual("admin-1", Rol.ADMIN);
    private static final UsuarioActual VENDEDOR = new UsuarioActual("vendedor-1", Rol.VENDEDOR);
    private static final ConfiguracionRequest PETICION =
            new ConfiguracionRequest("  Papelería Monpoint ", "mon850101ab1", "Av. Juárez 10", "  ");

    @Mock
    private ConfiguracionRepository configuracionRepository;

    private ConfiguracionService servicio;

    @BeforeEach
    void preparar() {
        servicio = new ConfiguracionService(configuracionRepository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void guardar_TruncaLaFechaAMilisegundosComoLaGuardaMongo() {
        Clock conNanos = Clock.fixed(Instant.parse("2026-11-08T16:25:25.583839676Z"), ZoneOffset.UTC);
        servicio = new ConfiguracionService(configuracionRepository, conNanos);
        when(configuracionRepository.findById(Configuracion.ID_UNICO)).thenReturn(Optional.empty());
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionResponse respuesta = servicio.guardar(PETICION, ADMIN);

        assertThat(respuesta.fechaActualizacion()).isEqualTo(Instant.parse("2026-11-08T16:25:25.583Z"));
    }

    @Test
    void obtener_SinConfiguracion_Lanza404() {
        when(configuracionRepository.findById(Configuracion.ID_UNICO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener())
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtener_ConConfiguracion_DevuelveSusDatos() {
        Configuracion existente = Configuracion.nueva("Monpoint", "MON850101AB1", "Calle 1", "3312345678", AHORA);
        when(configuracionRepository.findById(Configuracion.ID_UNICO)).thenReturn(Optional.of(existente));

        ConfiguracionResponse respuesta = servicio.obtener();

        assertThat(respuesta.nombreEmpresa()).isEqualTo("Monpoint");
        assertThat(respuesta.rfc()).isEqualTo("MON850101AB1");
    }

    @Test
    void guardar_ComoAdminSinConfiguracion_CreaElDocumentoUnicoNormalizado() {
        when(configuracionRepository.findById(Configuracion.ID_UNICO)).thenReturn(Optional.empty());
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConfiguracionResponse respuesta = servicio.guardar(PETICION, ADMIN);

        ArgumentCaptor<Configuracion> guardada = ArgumentCaptor.forClass(Configuracion.class);
        verify(configuracionRepository).save(guardada.capture());
        assertThat(guardada.getValue().getId()).isEqualTo(Configuracion.ID_UNICO);
        assertThat(guardada.getValue().getNombreEmpresa()).isEqualTo("Papelería Monpoint");
        assertThat(guardada.getValue().getRfc()).isEqualTo("MON850101AB1");
        assertThat(guardada.getValue().getTelefono()).isNull();
        assertThat(respuesta.fechaActualizacion()).isEqualTo(AHORA);
    }

    @Test
    void guardar_ComoAdminConConfiguracion_ActualizaElMismoDocumento() {
        Configuracion existente = Configuracion.nueva("Viejo", "VIE850101AB1", null, null,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(configuracionRepository.findById(Configuracion.ID_UNICO)).thenReturn(Optional.of(existente));
        when(configuracionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        servicio.guardar(PETICION, ADMIN);

        verify(configuracionRepository).save(existente);
        assertThat(existente.getNombreEmpresa()).isEqualTo("Papelería Monpoint");
        assertThat(existente.getDireccion()).isEqualTo("Av. Juárez 10");
        assertThat(existente.getFechaActualizacion()).isEqualTo(AHORA);
    }

    @Test
    void guardar_ComoVendedor_Lanza403SinTocarLaBase() {
        assertThatThrownBy(() -> servicio.guardar(PETICION, VENDEDOR))
                .isInstanceOf(AccesoDenegadoException.class);

        verifyNoInteractions(configuracionRepository);
    }
}
