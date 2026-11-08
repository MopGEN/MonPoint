package com.monpoint.reportes.service;

import com.monpoint.reportes.dto.request.ConfiguracionRequest;
import com.monpoint.reportes.dto.response.ConfiguracionResponse;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RecursoNoEncontradoException;
import com.monpoint.reportes.model.Configuracion;
import com.monpoint.reportes.repository.ConfiguracionRepository;
import com.monpoint.reportes.security.UsuarioActual;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Configuración de la empresa (H-050): un solo documento. Leer lo pueden ambos roles; modificar, solo ADMIN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository configuracionRepository;
    private final Clock clock;

    public ConfiguracionResponse obtener() {
        return configuracionRepository.findById(Configuracion.ID_UNICO)
                .map(ConfiguracionResponse::from)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "La configuración de la empresa todavía no se ha registrado"));
    }

    /** Crea el documento si no existe; si ya existe, lo actualiza (nunca hay dos). */
    public ConfiguracionResponse guardar(ConfiguracionRequest request, UsuarioActual ejecutor) {
        if (!ejecutor.esAdmin()) {
            throw new AccesoDenegadoException("Solo un ADMIN puede modificar la configuración de la empresa");
        }
        Instant ahora = clock.instant();
        Configuracion configuracion = configuracionRepository.findById(Configuracion.ID_UNICO)
                .map(existente -> {
                    existente.actualizar(request.nombreEmpresa(), request.rfc(), request.direccion(),
                            request.telefono(), ahora);
                    return existente;
                })
                .orElseGet(() -> Configuracion.nueva(request.nombreEmpresa(), request.rfc(), request.direccion(),
                        request.telefono(), ahora));

        Configuracion guardada = configuracionRepository.save(configuracion);
        log.info("evento=CONFIGURACION_GUARDADA usuarioId={}", ejecutor.id());
        return ConfiguracionResponse.from(guardada);
    }
}
