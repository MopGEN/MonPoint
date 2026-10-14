package com.monpoint.auth.audit;

import com.monpoint.auth.model.Rol;
import com.monpoint.auth.model.Usuario;
import com.monpoint.auth.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Bitácora de eventos de seguridad (RNF-13) en formato {@code clave=valor}, fácil de filtrar
 * con grep o de indexar en un agregador de logs.
 * <p>
 * No lee el {@code X-Request-ID} por su cuenta: {@link RequestIdFilter} ya lo dejó en el MDC y
 * {@code logging.pattern.level} lo imprime en cada línea, así que un evento se puede
 * seguir desde el API Gateway hasta aquí. Nunca registra contraseñas ni tokens.
 */
@Slf4j
@Service
public class AuthAuditService {

    /** Solo para la bitácora interna: al cliente siempre se le responde el mismo 401 opaco. */
    public enum MotivoLoginFallido { USUARIO_NO_ENCONTRADO, PASSWORD_INCORRECTA, USUARIO_INACTIVO }

    public void loginExitoso(Usuario usuario) {
        log.info("evento=LOGIN_EXITOSO usuarioId={} correo={} rol={}",
                usuario.getId(), usuario.getCorreo(), usuario.getRol());
    }

    public void loginFallido(String correo, MotivoLoginFallido motivo) {
        log.warn("evento=LOGIN_FALLIDO correo={} motivo={}", correo, motivo);
    }

    public void usuarioCreado(Usuario usuario, UserPrincipal ejecutor) {
        log.info("evento=USUARIO_CREADO usuarioId={} correo={} rol={} ejecutorId={}",
                usuario.getId(), usuario.getCorreo(), usuario.getRol(), ejecutor.id());
    }

    public void usuarioModificado(Usuario usuario, UserPrincipal ejecutor, Rol rolAnterior, boolean cambioPassword) {
        log.info("evento=USUARIO_MODIFICADO usuarioId={} correo={} rolAnterior={} rol={} cambioPassword={} ejecutorId={}",
                usuario.getId(), usuario.getCorreo(), rolAnterior, usuario.getRol(), cambioPassword, ejecutor.id());
    }

    public void usuarioDesactivado(Usuario usuario, UserPrincipal ejecutor) {
        log.info("evento=USUARIO_DESACTIVADO usuarioId={} correo={} ejecutorId={}",
                usuario.getId(), usuario.getCorreo(), ejecutor.id());
    }
}
