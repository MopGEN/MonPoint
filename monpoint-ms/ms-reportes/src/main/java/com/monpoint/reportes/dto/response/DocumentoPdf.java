package com.monpoint.reportes.dto.response;

/**
 * PDF ya generado y el nombre con el que se ofrece. El controlador lo convierte en la respuesta HTTP.
 * Es de uso interno: nunca se serializa a JSON.
 */
public record DocumentoPdf(String nombreArchivo, byte[] contenido) {
}
