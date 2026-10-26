package com.monpoint.ventas.controller;

import com.monpoint.ventas.dto.request.VentaRequest;
import com.monpoint.ventas.dto.response.VentaResponse;
import com.monpoint.ventas.security.UsuarioActual;
import com.monpoint.ventas.service.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ventas")
@RequiredArgsConstructor
public class VentaController {
    private final VentaService ventas;

    @PostMapping
    public ResponseEntity<VentaResponse> registrar(@RequestHeader("X-User-Id") String usuarioId,
                                                  @RequestHeader("X-User-Role") String rol,
                                                  @Valid @RequestBody VentaRequest solicitud) {
        return ResponseEntity.accepted().body(ventas.registrar(solicitud, new UsuarioActual(usuarioId, rol)));
    }
}
