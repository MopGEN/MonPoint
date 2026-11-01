package com.monpoint.ventas.controller;

import com.monpoint.ventas.audit.RequestIdFilter;
import com.monpoint.ventas.exception.AccesoDenegadoException;
import com.monpoint.ventas.exception.VentaInvalidaException;
import com.monpoint.ventas.exception.VentaNoEncontradaException;
import com.monpoint.ventas.exception.NoAutenticadoException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido",
                "El cuerpo de la petición falta o no es un JSON válido", request);
    }

    /** Sin X-User-Id / X-User-Role la petición no pasó por el Gateway: 401. Otra cabecera faltante: 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail cabeceraFaltante(MissingRequestHeaderException ex, HttpServletRequest request) {
        if (ex.getHeaderName().startsWith("X-User-")) {
            return problema(HttpStatus.UNAUTHORIZED, "No autenticado",
                    "Se requiere un token válido. Inicia sesión para obtener uno.", request);
        }
        return problema(HttpStatus.BAD_REQUEST, "Cabecera faltante",
                "Falta la cabecera " + ex.getHeaderName(), request);
    }

    /**
     * Parámetro, cabecera o variable de ruta con el tipo equivocado (p. ej. X-User-Role: OTRO, o letras en un
     * @PathVariable numérico). Sin este handler, el de Exception.class lo convertiría en un 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "El valor de '" + ex.getName() + "' no tiene el formato esperado", request);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ProblemDetail accesoDenegado(AccesoDenegadoException ex, HttpServletRequest request) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), request);
    }

    @ExceptionHandler(VentaInvalidaException.class)
    public ProblemDetail ventaInvalida(VentaInvalidaException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Venta inválida", ex.getMessage(), request);
    }

    /** Petición bien formada contra una venta que no existe (actividad 5.2). */
    @ExceptionHandler(VentaNoEncontradaException.class)
    public ProblemDetail ventaNoEncontrada(VentaNoEncontradaException ex, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Venta no encontrada", ex.getMessage(), request);
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ProblemDetail noAutenticado(NoAutenticadoException ex, HttpServletRequest request) {
        return problema(HttpStatus.UNAUTHORIZED, "No autenticado", ex.getMessage(), request);
    }

    /** Índice único violado. El mensaje de MongoDB trae detalles internos: no se expone. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicado(DuplicateKeyException ex, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "Registro duplicado",
                "Ya existe un registro con ese valor único", request);
    }

    /** Red de seguridad: las excepciones de Spring MVC conservan su 4xx; lo demás es 500 opaco con requestId. */
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
}
