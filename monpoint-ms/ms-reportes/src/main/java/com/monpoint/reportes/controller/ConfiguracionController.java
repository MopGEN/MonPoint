package com.monpoint.reportes.controller;

import com.monpoint.reportes.dto.request.ConfiguracionRequest;
import com.monpoint.reportes.dto.response.ConfiguracionResponse;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.ConfiguracionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración de la empresa (H-050). El prefijo es COMPLETO porque el Gateway no lo quita.
 * <p>
 * Las cabeceras de identidad van ANTES del {@code @RequestBody}: así la falta de identidad (401)
 * se detecta antes que un cuerpo inválido (400).
 */
@RestController
@RequestMapping("/reportes/configuracion")
@RequiredArgsConstructor
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    @GetMapping
    public ConfiguracionResponse obtener(@RequestHeader("X-User-Id") String usuarioId,
                                         @RequestHeader("X-User-Role") Rol rol) {
        return configuracionService.obtener();
    }

    @PutMapping
    public ConfiguracionResponse guardar(@RequestHeader("X-User-Id") String usuarioId,
                                         @RequestHeader("X-User-Role") Rol rol,
                                         @Valid @RequestBody ConfiguracionRequest request) {
        return configuracionService.guardar(request, new UsuarioActual(usuarioId, rol));
    }
}
