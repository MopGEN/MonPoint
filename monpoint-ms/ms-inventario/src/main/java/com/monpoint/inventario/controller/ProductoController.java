package com.monpoint.inventario.controller;

import com.monpoint.inventario.dto.request.ProductoRequest;
import com.monpoint.inventario.dto.response.ProductoResponse;
import com.monpoint.inventario.model.Rol;
import com.monpoint.inventario.security.UsuarioActual;
import com.monpoint.inventario.service.ProductoService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de productos (H-020, H-021). Delgado: traduce HTTP y delega. El prefijo es COMPLETO porque el Gateway no
 * lo quita. Las cabeceras de identidad van ANTES del {@code @RequestBody} (401 antes que 400).
 */
@RestController
@RequestMapping("/inventario/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@RequestHeader("X-User-Id") String usuarioId,
                                                  @RequestHeader("X-User-Role") Rol rol,
                                                  @Valid @RequestBody ProductoRequest request) {
        ProductoResponse creado = productoService.crear(request, new UsuarioActual(usuarioId, rol));
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /** Listado general: solo productos activos. */
    @GetMapping
    public List<ProductoResponse> listar(@RequestHeader("X-User-Id") String usuarioId,
                                         @RequestHeader("X-User-Role") Rol rol) {
        return productoService.listar();
    }

    /** Consulta directa: devuelve también los inactivos, con su estado. */
    @GetMapping("/{id}")
    public ProductoResponse obtener(@RequestHeader("X-User-Id") String usuarioId,
                                    @RequestHeader("X-User-Role") Rol rol,
                                    @PathVariable String id) {
        return productoService.obtener(id);
    }

    @PutMapping("/{id}")
    public ProductoResponse modificar(@RequestHeader("X-User-Id") String usuarioId,
                                      @RequestHeader("X-User-Role") Rol rol,
                                      @PathVariable String id,
                                      @Valid @RequestBody ProductoRequest request) {
        return productoService.modificar(id, request, new UsuarioActual(usuarioId, rol));
    }

    /** Baja lógica (H-021): marca el producto inactivo; el documento permanece. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> darDeBaja(@RequestHeader("X-User-Id") String usuarioId,
                                          @RequestHeader("X-User-Role") Rol rol,
                                          @PathVariable String id) {
        productoService.darDeBaja(id, new UsuarioActual(usuarioId, rol));
        return ResponseEntity.noContent().build();
    }
}
