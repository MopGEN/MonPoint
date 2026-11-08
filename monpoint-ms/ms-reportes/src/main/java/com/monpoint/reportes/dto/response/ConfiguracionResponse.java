package com.monpoint.reportes.dto.response;

import com.monpoint.reportes.model.Configuracion;

import java.time.Instant;

public record ConfiguracionResponse(String nombreEmpresa, String rfc, String direccion, String telefono,
                                    Instant fechaActualizacion) {

    public static ConfiguracionResponse from(Configuracion c) {
        return new ConfiguracionResponse(c.getNombreEmpresa(), c.getRfc(), c.getDireccion(), c.getTelefono(),
                c.getFechaActualizacion());
    }
}
