package com.monpoint.clientes.dto.response;

import com.monpoint.clientes.model.Cliente;

import java.time.Instant;

public record ClienteResponse(String id, String nombre, String correo, String telefono, String direccion,
                              Instant fechaRegistro, Instant fechaActualizacion) {

    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNombre(), c.getCorreo(), c.getTelefono(), c.getDireccion(),
                c.getFechaRegistro(), c.getFechaActualizacion());
    }
}
