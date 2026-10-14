package com.monpoint.inventario.dto.response;

import com.monpoint.inventario.model.Producto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductoResponse(String id, String nombre, String descripcion, BigDecimal precio,
                               int existencia, int umbralStock, boolean activo,
                               Instant fechaRegistro, Instant fechaActualizacion) {

    public static ProductoResponse from(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(),
                p.getExistencia(), p.getUmbralStock(), p.isActivo(),
                p.getFechaRegistro(), p.getFechaActualizacion());
    }
}
