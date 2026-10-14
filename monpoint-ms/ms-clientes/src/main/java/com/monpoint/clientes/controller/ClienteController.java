package com.monpoint.clientes.controller;

import com.monpoint.clientes.dto.request.ClienteRequest;
import com.monpoint.clientes.dto.response.ClienteResponse;
import com.monpoint.clientes.model.Rol;
import com.monpoint.clientes.security.UsuarioActual;
import com.monpoint.clientes.service.ClienteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de clientes (H-040). Delgado: traduce HTTP y delega. El prefijo es COMPLETO porque el Gateway no lo quita.
 * <p>
 * Las cabeceras de identidad van ANTES del {@code @RequestBody}: así la falta de identidad (401)
 * se detecta antes que un cuerpo inválido (400).
 */
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @PostMapping
    public ResponseEntity<ClienteResponse> crear(@RequestHeader("X-User-Id") String usuarioId,
                                                 @RequestHeader("X-User-Role") Rol rol,
                                                 @Valid @RequestBody ClienteRequest request) {
        ClienteResponse creado = clienteService.crear(request, new UsuarioActual(usuarioId, rol));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /** Sin {@code nombre} devuelve todos; con {@code nombre}, los que lo contengan (parcial, sin distinguir mayúsculas). */
    @GetMapping
    public List<ClienteResponse> listar(@RequestHeader("X-User-Id") String usuarioId,
                                        @RequestHeader("X-User-Role") Rol rol,
                                        @RequestParam(required = false) String nombre) {
        return clienteService.listar(nombre);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtener(@RequestHeader("X-User-Id") String usuarioId,
                                   @RequestHeader("X-User-Role") Rol rol,
                                   @PathVariable String id) {
        return clienteService.obtener(id);
    }

    @PutMapping("/{id}")
    public ClienteResponse modificar(@RequestHeader("X-User-Id") String usuarioId,
                                     @RequestHeader("X-User-Role") Rol rol,
                                     @PathVariable String id,
                                     @Valid @RequestBody ClienteRequest request) {
        return clienteService.modificar(id, request, new UsuarioActual(usuarioId, rol));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@RequestHeader("X-User-Id") String usuarioId,
                                         @RequestHeader("X-User-Role") Rol rol,
                                         @PathVariable String id) {
        clienteService.eliminar(id, new UsuarioActual(usuarioId, rol));
        return ResponseEntity.noContent().build();
    }
}
