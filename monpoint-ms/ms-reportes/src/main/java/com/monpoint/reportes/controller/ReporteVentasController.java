package com.monpoint.reportes.controller;

import com.monpoint.reportes.dto.response.AgregadoVentasResponse;
import com.monpoint.reportes.model.Periodo;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.AgregacionVentasService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Reportes de ventas. Las cabeceras de identidad van primero: sin identidad responde 401 antes de validar
 * los parámetros.
 */
@RestController
@RequestMapping("/reportes/ventas")
@RequiredArgsConstructor
public class ReporteVentasController {

    private final AgregacionVentasService agregacionVentasService;

    /** Ejemplo: {@code GET /reportes/ventas/agregado?periodo=DIA&desde=2026-11-01&hasta=2026-11-30}. */
    @GetMapping("/agregado")
    public AgregadoVentasResponse agregado(@RequestHeader("X-User-Id") String usuarioId,
                                           @RequestHeader("X-User-Role") Rol rol,
                                           @RequestParam Periodo periodo,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agregacionVentasService.agregar(periodo, desde, hasta, new UsuarioActual(usuarioId, rol));
    }
}
