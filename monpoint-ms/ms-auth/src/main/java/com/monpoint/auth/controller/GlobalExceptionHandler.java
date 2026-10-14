package com.monpoint.auth.controller;

import com.monpoint.auth.audit.RequestIdFilter;
import com.monpoint.auth.exception.CorreoDuplicadoException;
import com.monpoint.auth.exception.CredencialesInvalidasException;
import com.monpoint.auth.exception.ModificacionRolPropioException;
import com.monpoint.auth.exception.OperacionInvalidaException;
import com.monpoint.auth.exception.PasswordValidationException;
import com.monpoint.auth.exception.UsuarioNoEncontradoException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones de los controladores a ProblemDetail (RFC 9457), siempre con el
 * {@code requestId} de la petición.
 * <p>
 * No extiende {@code ResponseEntityExceptionHandler}: este ya trae handlers para
 * {@code MethodArgumentNotValidException} y otras, y redefinirlos aquí sería un mapeo ambiguo.
 * Los 401/403 de la cadena de filtros los resuelven {@code CustomAuthenticationEntryPoint} y
 * {@code CustomAccessDeniedHandler}, porque ocurren antes de llegar a un controlador.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** H-010: mismo mensaje opaco sin importar si falló el correo, la contraseña o el estado. */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ProblemDetail credencialesInvalidas(CredencialesInvalidasException ex, HttpServletRequest request) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail datosInvalidos(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> campos = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> Objects.requireNonNullElse(error.getDefaultMessage(), "Valor inválido"),
                        (primero, segundo) -> primero + "; " + segundo,
                        TreeMap::new));
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Uno o más campos no cumplen las validaciones", request);
        problema.setProperty("campos", campos);
        return problema;
    }

    /** JSON mal formado, cuerpo ausente o un valor que no encaja con el tipo (p. ej. {@code "rol": "OTRO"}). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido", describir(ex), request);
    }

    /** H-012: se devuelven todas las reglas incumplidas, no solo la primera. */
    @ExceptionHandler(PasswordValidationException.class)
    public ProblemDetail passwordInsegura(PasswordValidationException ex, HttpServletRequest request) {
        ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Contraseña insegura", ex.getMessage(), request);
        problema.setProperty("errores", ex.getErrores());
        return problema;
    }

    /**
     * {@link DuplicateKeyException} llega cuando dos altas simultáneas pasan la validación previa
     * y el índice único frena a la segunda. Su mensaje trae detalles internos de MongoDB, así que
     * no se expone.
     */
    @ExceptionHandler({CorreoDuplicadoException.class, DuplicateKeyException.class})
    public ProblemDetail correoDuplicado(RuntimeException ex, HttpServletRequest request) {
        String detalle = ex instanceof CorreoDuplicadoException
                ? ex.getMessage()
                : "Ya existe un usuario con ese correo";
        return problema(HttpStatus.CONFLICT, "Correo duplicado", detalle, request);
    }

    /** H-013. */
    @ExceptionHandler(ModificacionRolPropioException.class)
    public ProblemDetail rolPropio(ModificacionRolPropioException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Operación no permitida", ex.getMessage(), request);
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ProblemDetail operacionInvalida(OperacionInvalidaException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Operación no permitida", ex.getMessage(), request);
    }

    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ProblemDetail usuarioNoEncontrado(UsuarioNoEncontradoException ex, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Usuario no encontrado", ex.getMessage(), request);
    }

    /** Bloqueos decididos en el servicio (p. ej. un VENDEDOR modificando a otro usuario). */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex, HttpServletRequest request) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ProblemDetail rutaInexistente(Exception ex, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Ruta no encontrada", "No existe la ruta solicitada", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> metodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) {
        ProblemDetail problema = problema(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "La ruta no admite el método " + ex.getMethod(), request);
        // Conserva el header Allow con los métodos que sí acepta la ruta.
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(problema);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> tipoNoSoportado(HttpMediaTypeNotSupportedException ex,
                                                         HttpServletRequest request) {
        ProblemDetail problema = problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado",
                "Envía el cuerpo como application/json", request);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(ex.getHeaders()).body(problema);
    }

    /**
     * Red de seguridad. Las demás excepciones de Spring MVC implementan {@link ErrorResponse} y
     * conservan su código 4xx; cualquier otra cosa es un error inesperado: 500 opaco para el
     * cliente y el detalle completo en el log, ligado por el {@code requestId}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            ProblemDetail problema = errorResponse.getBody();
            problema.setInstance(URI.create(request.getRequestURI()));
            problema.setProperty("requestId", RequestIdFilter.obtener(request));
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(problema);
        }
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Si persiste, repórtalo con el requestId.", request));
    }

    private static ProblemDetail problema(HttpStatus status, String titulo, String detalle,
                                          HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("requestId", RequestIdFilter.obtener(request));
        return problema;
    }

    /** Explica qué falló sin exponer el mensaje interno de Jackson. */
    private static String describir(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException error && !error.getPath().isEmpty()) {
            String campo = error.getPath().getLast().getPropertyName();
            Class<?> tipo = error.getTargetType();
            if (campo != null && tipo != null && tipo.isEnum()) {
                return "El campo '" + campo + "' solo admite: " + Arrays.toString(tipo.getEnumConstants());
            }
            if (campo != null) {
                return "El campo '" + campo + "' tiene un formato inválido";
            }
        }
        return "El cuerpo de la petición falta o no es un JSON válido";
    }
}
