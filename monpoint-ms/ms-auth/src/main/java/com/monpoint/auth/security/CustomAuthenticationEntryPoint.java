package com.monpoint.auth.security;

import com.monpoint.auth.audit.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;

/**
 * 401 en formato ProblemDetail (RFC 9457) cuando una ruta protegida llega sin un token válido:
 * ausente, expirado, alterado o mal formado.
 * <p>
 * Un {@code @RestControllerAdvice} no puede atender este caso porque ocurre en la cadena de
 * filtros, antes de llegar a los controladores.
 */
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Se requiere un token válido. Inicia sesión para obtener uno.");
        problema.setTitle("No autenticado");
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("requestId", RequestIdFilter.obtener(request));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}
