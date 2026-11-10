package com.monpoint.notificaciones.controller;

import com.monpoint.notificaciones.model.Notificacion;
import com.monpoint.notificaciones.model.Rol;
import com.monpoint.notificaciones.security.UsuarioActual;
import com.monpoint.notificaciones.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
@RestController
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {
    private final NotificacionService servicio;
    @GetMapping
    public List<Notificacion> listar(@RequestHeader("X-User-Id") String id,
            @RequestHeader("X-User-Role") Rol rol, @RequestParam(required = false) Boolean leida) {
        return servicio.listar(leida, new UsuarioActual(id, rol));
    }
    @PutMapping("/{id}/leida")
    public ResponseEntity<Void> leer(@RequestHeader("X-User-Id") String usuario,
            @RequestHeader("X-User-Role") Rol rol, @PathVariable String id) {
        servicio.leer(id, new UsuarioActual(usuario, rol));
        return ResponseEntity.noContent().build();
    }
}
