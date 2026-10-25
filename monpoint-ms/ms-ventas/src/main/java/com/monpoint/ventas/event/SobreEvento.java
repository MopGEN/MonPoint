package com.monpoint.ventas.event;

import java.util.Map;

/**
 * Sobre común de todo evento del sistema (Doc 02 §9.2). Es el único campo de esta forma que los tres servicios
 * comparten literalmente; ms-inventario y ms-clientes declaran el suyo idéntico.
 * <p>
 * No hay librería compartida entre servicios a propósito: cada uno declara su copia. Duplicar seis campos es
 * más barato que acoplar los despliegues de seis servicios a la versión de un artefacto común.
 */
public record SobreEvento(String eventoId, String tipo, String version, String fecha, String origen,
                          Map<String, Object> datos) {
}
