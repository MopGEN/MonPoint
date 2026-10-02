package com.monpoint.auth.controller;

import com.monpoint.auth.dto.request.LoginRequest;
import com.monpoint.auth.dto.request.RegistroRequest;
import com.monpoint.auth.dto.response.AuthResponse;
import com.monpoint.auth.dto.response.UsuarioResponse;
import com.monpoint.auth.security.UserPrincipal;
import com.monpoint.auth.service.AuthService;
import com.monpoint.auth.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, alta de usuarios y perfil propio. Qué rol entra a cada ruta lo define {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** Solo ADMIN. */
    @PostMapping("/register")
    public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest request,
                                                     @AuthenticationPrincipal UserPrincipal ejecutor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrar(request, ejecutor));
    }

    /** El usuario del token, con su estado actual en la BD (incluye nombre y si sigue activo). */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> perfil(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(usuarioService.obtenerPorId(principal.id()));
    }
}
