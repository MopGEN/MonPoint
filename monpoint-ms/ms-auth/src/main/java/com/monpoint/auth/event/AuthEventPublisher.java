package com.monpoint.auth.event;

import com.monpoint.auth.model.Usuario;

/**
 * Puerto de salida para los eventos de ms-auth: desacopla a los servicios del medio de
 * publicación (hoy un log, más adelante RabbitMQ) sin tocar su código.
 * <p>
 * Contrato fail-safe: una implementación nunca propaga excepciones al llamador. Si el broker
 * falla, lo registra y el alta del usuario sigue su curso.
 */
public interface AuthEventPublisher {

    void publicarUsuarioCreado(Usuario usuario);
}
