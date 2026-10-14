package com.monpoint.auth.audit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Garantiza un {@code X-Request-ID} por petición para correlacionar con el API Gateway (RNF-13).
 * <p>
 * Reutiliza el que manda el Gateway o genera uno, y lo expone en la respuesta, en el MDC de los
 * logs y en {@link #obtener(HttpServletRequest)} para los {@code ProblemDetail}. Corre antes que
 * Spring Security para que los 401/403 de la cadena de filtros también lo lleven.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final String ATRIBUTO = RequestIdFilter.class.getName() + ".requestId";
    /** Descarta valores con saltos de línea u otros caracteres que permitirían inyectar texto en logs o cabeceras. */
    private static final Pattern FORMATO_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolver(request.getHeader(HEADER));
        request.setAttribute(ATRIBUTO, requestId);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** El identificador de la petición en curso: el del Gateway o el generado por este filtro. */
    public static String obtener(HttpServletRequest request) {
        Object requestId = request.getAttribute(ATRIBUTO);
        return requestId != null ? requestId.toString() : resolver(request.getHeader(HEADER));
    }

    private static String resolver(String recibido) {
        return recibido != null && FORMATO_VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
    }
}
