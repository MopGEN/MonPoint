package com.monpoint.inventario.service;

import com.monpoint.inventario.dto.request.ProductoRequest;
import com.monpoint.inventario.dto.response.ProductoResponse;
import com.monpoint.inventario.security.UsuarioActual;

import java.util.List;

public interface ProductoService {

    /** Solo ADMIN (H-020). */
    ProductoResponse crear(ProductoRequest request, UsuarioActual ejecutor);

    /** Solo productos activos (H-021). */
    List<ProductoResponse> listar();

    /** Incluye inactivos, con su estado (H-021). */
    ProductoResponse obtener(String id);

    /** Solo ADMIN (H-020). */
    ProductoResponse modificar(String id, ProductoRequest request, UsuarioActual ejecutor);

    /** Baja lógica, solo ADMIN (H-021). Si ya estaba de baja no hace nada ni publica de nuevo. */
    void darDeBaja(String id, UsuarioActual ejecutor);
}
