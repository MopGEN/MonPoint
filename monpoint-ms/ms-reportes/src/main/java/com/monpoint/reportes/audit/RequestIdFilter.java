package com.monpoint.reportes.audit;

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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    private static final String ATRIBUTO = RequestIdFilter.class.getName() + ".requestId";
    // Descarta saltos de linea y otros caracteres que permitirian inyectar texto en logs o cabeceras
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

//Da a cada petición un identificador para poder seguirla en los logs.
//
//@Component: hace que Spring lo cree y lo registre como filtro.
//@Order(Ordered.HIGHEST_PRECEDENCE): lo pone antes que los demás filtros, para que sus logs también lleven el identificador.
//OncePerRequestFilter: garantiza que corra una sola vez por petición.
//resolver: usa el identificador recibido si es válido; si no, genera un UUID.
//setAttribute y obtener: guardan el identificador en la petición para que el manejador de errores del paso 6 lo recupere.
//setHeader: devuelve el identificador en la respuesta.
//MDC.put: lo guarda en el contexto de logs del hilo; de ahí lo lee %X{requestId:-} de tus propiedades.
//filterChain.doFilter: deja que la petición siga su camino.
//finally con MDC.remove: Tomcat reutiliza hilos, y sin esto el identificador se colaría en otra petición.
//Imports jakarta.servlet.*: son los tipos de petición, respuesta y cadena de filtros.
//Imports java.*: IOException lo exige la firma del metodo, UUID genera identificadores y Pattern valida el formato.