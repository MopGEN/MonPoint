package com.monpoint.auth.controller;

import com.monpoint.auth.dto.request.ActualizarUsuarioRequest;
import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestión de usuarios (H-011, H-013). Las reglas de quién modifica a quién viven en
 * {@code UsuarioServiceImpl}; aquí solo se recibe la petición y se responde.
 */
@RestController
@RequestMapping("/auth/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /** Solo ADMIN. */
    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarActivos() {
        return ResponseEntity.ok(usuarioService.listarActivos());
    }

    /** Cualquier usuario autenticado; un VENDEDOR solo sobre su propio id. */
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> modificar(@PathVariable String id,
                                                     @Valid @RequestBody ActualizarUsuarioRequest request,
                                                     @AuthenticationPrincipal UserPrincipal ejecutor) {
        return ResponseEntity.ok(usuarioService.modificar(id, request, ejecutor));
    }

    /** Solo ADMIN, y nunca sobre su propia cuenta. */
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivar(@PathVariable String id,
                                           @AuthenticationPrincipal UserPrincipal ejecutor) {
        usuarioService.desactivar(id, ejecutor);
        return ResponseEntity.noContent().build();
    }
}
