package com.monpoint.clientes.event;

import com.monpoint.clientes.model.Cliente;

/**
 * Puerto de salida de los eventos de ms-clientes (RF-4.6): ClienteCreado y ClienteActualizado.
 * <p>
 * Contrato fail-safe: una implementación nunca propaga excepciones al llamador. Si el broker falla,
 * lo registra y la operación del cliente sigue su curso.
 */
public interface ClienteEventPublisher {

    void publicarClienteCreado(Cliente cliente);

    void publicarClienteActualizado(Cliente cliente);
}
