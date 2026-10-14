package com.monpoint.inventario.service.impl;

import com.monpoint.inventario.dto.request.ProductoRequest;
import com.monpoint.inventario.dto.response.ProductoResponse;
import com.monpoint.inventario.event.InventarioEventPublisher;
import com.monpoint.inventario.exception.AccesoDenegadoException;
import com.monpoint.inventario.exception.RecursoNoEncontradoException;
import com.monpoint.inventario.model.Producto;
import com.monpoint.inventario.repository.ProductoRepository;
import com.monpoint.inventario.security.UsuarioActual;
import com.monpoint.inventario.service.ProductoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reglas de H-020, H-021 y H-022. Alta, modificación y baja exigen ADMIN; la consulta admite ambos roles
 * (RNF-07: el rol se verifica aquí, no en el controlador).
 * <p>
 * ⚠️ No definido en la documentación: el verbo de la baja (se usa {@code DELETE}) y si se puede modificar un
 * producto ya dado de baja (se permite; no se inventa una regla).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final InventarioEventPublisher eventPublisher;

    @Override
    @Transactional
    public ProductoResponse crear(ProductoRequest request, UsuarioActual ejecutor) {
        exigirAdmin(ejecutor, "Solo un ADMIN puede dar de alta productos");
        Producto guardado = productoRepository.save(Producto.crear(request.nombre(), request.descripcion(),
                request.precio(), request.existencia(), request.umbralStock()));
        log.info("evento=PRODUCTO_CREADO productoId={} usuarioId={}", guardado.getId(), ejecutor.id());
        eventPublisher.publicarProductoCreado(guardado);
        return ProductoResponse.from(guardado);
    }

    @Override
    public List<ProductoResponse> listar() {
        return productoRepository.findByActivoTrue().stream().map(ProductoResponse::from).toList();
    }

    @Override
    public ProductoResponse obtener(String id) {
        return ProductoResponse.from(buscar(id));
    }

    @Override
    @Transactional
    public ProductoResponse modificar(String id, ProductoRequest request, UsuarioActual ejecutor) {
        exigirAdmin(ejecutor, "Solo un ADMIN puede modificar productos");
        Producto producto = buscar(id);
        producto.modificar(request.nombre(), request.descripcion(), request.precio(), request.existencia(),
                request.umbralStock());
        Producto guardado = productoRepository.save(producto);
        log.info("evento=PRODUCTO_ACTUALIZADO productoId={} usuarioId={}", guardado.getId(), ejecutor.id());
        eventPublisher.publicarProductoActualizado(guardado);
        return ProductoResponse.from(guardado);
    }

    @Override
    @Transactional
    public void darDeBaja(String id, UsuarioActual ejecutor) {
        exigirAdmin(ejecutor, "Solo un ADMIN puede dar de baja productos");
        Producto producto = buscar(id);
        if (!producto.isActivo()) {
            log.info("evento=PRODUCTO_YA_ESTABA_DE_BAJA productoId={}", id);
            return;
        }
        producto.darDeBaja();
        Producto guardado = productoRepository.save(producto);
        log.info("evento=PRODUCTO_DADO_DE_BAJA productoId={} usuarioId={}", id, ejecutor.id());
        eventPublisher.publicarProductoDadoDeBaja(guardado);
    }

    private static void exigirAdmin(UsuarioActual ejecutor, String mensaje) {
        if (!ejecutor.esAdmin()) {
            throw new AccesoDenegadoException(mensaje);
        }
    }

    private Producto buscar(String id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el producto con id " + id));
    }
}
