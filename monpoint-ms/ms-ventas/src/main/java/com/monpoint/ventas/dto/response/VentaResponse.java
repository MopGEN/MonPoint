package com.monpoint.ventas.dto.response;

import com.monpoint.ventas.model.EstadoVenta;
import com.monpoint.ventas.model.Venta;

public record VentaResponse(String id, long folio, EstadoVenta estado) {
    public static VentaResponse de(Venta venta) {
        return new VentaResponse(venta.getId(), venta.getFolio(), venta.getEstado());
    }
}
