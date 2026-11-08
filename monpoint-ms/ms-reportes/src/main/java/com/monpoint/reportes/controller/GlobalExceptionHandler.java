package com.monpoint.reportes.controller;

import com.monpoint.reportes.audit.RequestIdFilter;
import com.monpoint.reportes.exception.AccesoDenegadoException;
import com.monpoint.reportes.exception.RangoInvalidoException;
import com.monpoint.reportes.exception.RecursoNoEncontradoException;
import com.monpoint.reportes.exception.VentaCanceladaException;
import com.monpoint.reportes.exception.VentaNoDisponibleException;
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
import org.springframework.web.bind.MissingServletRequestParameterException;
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

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail cabeceraFaltante(MissingRequestHeaderException ex, HttpServletRequest request) {
        if (ex.getHeaderName().startsWith("X-User-")) {
            return problema(HttpStatus.UNAUTHORIZED, "No autenticado",
                    "Se requiere un token válido. Inicia sesión para obtener uno.", request);
        }
        return problema(HttpStatus.BAD_REQUEST, "Cabecera faltante",
                "Falta la cabecera " + ex.getHeaderName(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro inválido",
                "El valor de '" + ex.getName() + "' no tiene el formato esperado", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail parametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetro faltante",
                "Falta el parámetro '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(RangoInvalidoException.class)
    public ProblemDetail rangoInvalido(RangoInvalidoException ex, HttpServletRequest request) {
        return problema(HttpStatus.BAD_REQUEST, "Rango inválido", ex.getMessage(), request);
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ProblemDetail accesoDenegado(AccesoDenegadoException ex, HttpServletRequest request) {
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), request);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest request) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request);
    }

    @ExceptionHandler(VentaNoDisponibleException.class)
    public ProblemDetail ventaNoDisponible(VentaNoDisponibleException ex, HttpServletRequest request) {
        ProblemDetail problema = problema(HttpStatus.NOT_FOUND, "Venta no disponible", ex.getMessage(), request);
        problema.setProperty("codigo", VentaNoDisponibleException.CODIGO);
        return problema;
    }

    @ExceptionHandler(VentaCanceladaException.class)
    public ProblemDetail ventaCancelada(VentaCanceladaException ex, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "Venta cancelada", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ProblemDetail duplicado(DuplicateKeyException ex, HttpServletRequest request) {
        return problema(HttpStatus.CONFLICT, "Registro duplicado",
                "Ya existe un registro con ese valor único", request);
    }

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