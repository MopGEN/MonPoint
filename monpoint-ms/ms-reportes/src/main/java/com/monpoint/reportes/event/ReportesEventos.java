package com.monpoint.reportes.event;

/**
 * Nombres de la topología que consume ms-reportes. Los exchanges y routing keys se copian de los publicadores
 * ({@code VentasEventos}, {@code InventarioEventos}, {@code ClienteEventos}); las colas son propias.
 * <p>
 * Ventas se enlaza con dos routing keys EXPLÍCITAS y no con {@code venta.*}: ese patrón también atraparía
 * {@code venta.creada}, que aquí no se usa (una venta pendiente no tiene proyección, Doc 04).
 */
public final class ReportesEventos {

    public static final String EXCHANGE_VENTAS = "ventas.eventos";
    public static final String EXCHANGE_INVENTARIO = "inventario.eventos";
    public static final String EXCHANGE_CLIENTES = "clientes.eventos";

    public static final String RK_VENTA_CONFIRMADA = "venta.confirmada";
    public static final String RK_VENTA_CANCELADA = "venta.cancelada";
    /** producto.creado, producto.actualizado y producto.dado-de-baja; no atrapa stock.*. */
    public static final String RK_PRODUCTO_TODOS = "producto.*";
    /** cliente.creado y cliente.actualizado (ms-clientes no publica bajas). */
    public static final String RK_CLIENTE_TODOS = "cliente.*";

    public static final String TIPO_VENTA_CONFIRMADA = "VentaConfirmada";
    public static final String TIPO_VENTA_CANCELADA = "VentaCancelada";

    public static final String COLA_VENTAS = "reportes.ventas";
    public static final String COLA_VENTAS_FALLIDOS = "reportes.ventas.fallidos";
    public static final String COLA_PRODUCTOS = "reportes.productos";
    public static final String COLA_PRODUCTOS_FALLIDOS = "reportes.productos.fallidos";
    public static final String COLA_CLIENTES = "reportes.clientes";
    public static final String COLA_CLIENTES_FALLIDOS = "reportes.clientes.fallidos";

    private ReportesEventos() {
    }
}
