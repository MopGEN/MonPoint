package com.monpoint.reportes.controller;

import com.monpoint.reportes.dto.response.DocumentoPdf;
import com.monpoint.reportes.model.Rol;
import com.monpoint.reportes.security.UsuarioActual;
import com.monpoint.reportes.service.DocumentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Documentos PDF (H-051 a H-053). Las cabeceras de identidad van primero: sin identidad responde 401 antes de
 * validar los parámetros.
 * <p>
 * No se declara {@code produces = application/pdf}: si se declarara, una petición con
 * {@code Accept: application/json} recibiría 406 antes de llegar aquí, y los errores ya salen como
 * {@code ProblemDetail} desde {@link GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/reportes")
@RequiredArgsConstructor
public class DocumentoController {

    private final DocumentoService documentoService;

    /** ADMIN y VENDEDOR: el cajero entrega el comprobante al cliente. */
    @GetMapping("/ventas/{id}/ticket")
    public ResponseEntity<byte[]> ticket(@RequestHeader("X-User-Id") String usuarioId,
                                         @RequestHeader("X-User-Role") Rol rol,
                                         @PathVariable String id) {
        return pdf(documentoService.ticket(id));
    }

    /** Ejemplo: {@code GET /reportes/ventas/historial?desde=2026-11-01&hasta=2026-11-30}. */
    @GetMapping("/ventas/historial")
    public ResponseEntity<byte[]> historial(@RequestHeader("X-User-Id") String usuarioId,
                                            @RequestHeader("X-User-Role") Rol rol,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return pdf(documentoService.historial(desde, hasta, new UsuarioActual(usuarioId, rol)));
    }

    @GetMapping("/productos/listado")
    public ResponseEntity<byte[]> listadoProductos(@RequestHeader("X-User-Id") String usuarioId,
                                                   @RequestHeader("X-User-Role") Rol rol) {
        return pdf(documentoService.listadoProductos(new UsuarioActual(usuarioId, rol)));
    }

    @GetMapping("/clientes/listado")
    public ResponseEntity<byte[]> listadoClientes(@RequestHeader("X-User-Id") String usuarioId,
                                                  @RequestHeader("X-User-Role") Rol rol) {
        return pdf(documentoService.listadoClientes(new UsuarioActual(usuarioId, rol)));
    }

    /**
     * {@code inline}: el navegador lo abre en su visor en lugar de descargarlo; el nombre se usa si el usuario
     * lo guarda. {@code no-store}: son datos del negocio y no deben quedar en cachés intermedias.
     */
    private static ResponseEntity<byte[]> pdf(DocumentoPdf documento) {
        // Los nombres se arman en DocumentoService solo con ASCII (folio, fechas ISO), sin datos del usuario.
        ContentDisposition disposicion = ContentDisposition.inline().filename(documento.nombreArchivo()).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .cacheControl(CacheControl.noStore())
                .body(documento.contenido());
    }
}
