package com.monpoint.auth.security;

import com.monpoint.auth.audit.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

/**
 * 403 en formato ProblemDetail cuando el token es válido pero el rol no alcanza para la ruta
 * (p. ej. un VENDEDOR intentando registrar usuarios). Sin este handler la respuesta iría vacía.
 */
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Tu rol no tiene permiso para realizar esta operación.");
        problema.setTitle("Acceso denegado");
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("requestId", RequestIdFilter.obtener(request));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
