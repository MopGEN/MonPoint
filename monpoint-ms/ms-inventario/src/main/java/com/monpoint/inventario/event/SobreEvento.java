package com.monpoint.inventario.event;

import java.util.Map;

/**
 * Sobre común de todo evento (Doc 03 §2.3, H-006, RNF-19): identificador único, tipo, versión de esquema,
 * fecha (ISO-8601 UTC), servicio productor y contenido.
 */
public record SobreEvento(String eventoId, String tipo, String version, String fecha, String origen,
                          Map<String, Object> datos) {
}
