package com.monpoint.reportes.repository;

import com.monpoint.reportes.model.Configuracion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra MongoDB real (docker compose up -d mongodb): comprueba que guardar con el _id fijo crea el
 * documento la primera vez y lo REEMPLAZA después, sin duplicarlo.
 * <p>
 * La base local puede tener la configuración real del desarrollador: se respalda antes y se restaura al final.
 */
@SpringBootTest(properties = {"eureka.client.enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
class ConfiguracionRepositoryTest {

    @Autowired
    private ConfiguracionRepository repositorio;

    private Optional<Configuracion> respaldo;

    @BeforeEach
    void respaldar() {
        respaldo = repositorio.findById(Configuracion.ID_UNICO);
        repositorio.deleteById(Configuracion.ID_UNICO);
    }

    @AfterEach
    void restaurar() {
        repositorio.deleteById(Configuracion.ID_UNICO);
        respaldo.ifPresent(repositorio::save);
    }

    @Test
    void guardarDosVeces_DejaUnSoloDocumentoConLosUltimosDatos() {
        repositorio.save(Configuracion.nueva("Primera", "PRI850101AB1", null, null,
                Instant.parse("2026-11-08T10:00:00Z")));

        Configuracion existente = repositorio.findById(Configuracion.ID_UNICO).orElseThrow();
        existente.actualizar("Segunda", "SEG850101AB1", "Calle 2", "33", Instant.parse("2026-11-08T11:00:00Z"));
        repositorio.save(existente);

        assertThat(repositorio.count()).isEqualTo(1);
        Configuracion leida = repositorio.findById(Configuracion.ID_UNICO).orElseThrow();
        assertThat(leida.getNombreEmpresa()).isEqualTo("Segunda");
        assertThat(leida.getRfc()).isEqualTo("SEG850101AB1");
        assertThat(leida.getFechaActualizacion()).isEqualTo(Instant.parse("2026-11-08T11:00:00Z"));
    }
}
