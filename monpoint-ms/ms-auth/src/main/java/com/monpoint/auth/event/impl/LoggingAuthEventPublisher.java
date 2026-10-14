package com.monpoint.auth.event.impl;

import com.monpoint.auth.event.AuthEventPublisher;
import com.monpoint.auth.model.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Implementación inicial: solo deja constancia en el log, así ms-auth no depende de RabbitMQ
 * en desarrollo. Cuando exista el bus, otra implementación la reemplaza.
 */
@Slf4j
@Component
public class LoggingAuthEventPublisher implements AuthEventPublisher {

    @Override
    public void publicarUsuarioCreado(Usuario usuario) {
        log.info("evento=USUARIO_CREADO_PUBLICADO usuarioId={} rol={} destino=log",
                usuario.getId(), usuario.getRol());
    }
}
