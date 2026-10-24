package com.monpoint.ventas.event;

/**
 * Nombres que usa ms-ventas en RabbitMQ.
 * <p>
 * A diferencia de {@code InventarioEventos} y {@code ClienteEventos}, estos valores <b>no son una propuesta</b>:
 * los de entrada están copiados literalmente del código ya fusionado de ms-inventario y ms-clientes, que es el
 * contrato de facto. Cambiar cualquiera de ellos rompe la integración en silencio, porque un mensaje con una
 * routing key sin enlace se descarta en el broker sin error.
 * <p>
 * Los de salida ({@code venta.*}) los fija ms-ventas. ms-inventario ya tiene su cola
 * {@code inventario.venta-creada} enlazada a {@link #RK_VENTA_CREADA}.
 */
public final class VentasEventos {

    // ---------------------------------------------------------------------
    // Exchanges — los tres de tipo topic, durables, sin borrado automático.
    // Declararlos en varios servicios es inocuo MIENTRAS los atributos
    // coincidan; con atributos distintos, el arranque falla.
    // ---------------------------------------------------------------------
    public static final String EXCHANGE = "ventas.eventos";
    public static final String EXCHANGE_INVENTARIO = "inventario.eventos";
    public static final String EXCHANGE_CLIENTES = "clientes.eventos";

    public static final String ORIGEN = "ms-ventas";
    public static final String VERSION = "1";

    // ---------------------------------------------------------------------
    // Lo que ms-ventas PUBLICA
    // ---------------------------------------------------------------------
    public static final String TIPO_VENTA_CREADA = "VentaCreada";
    public static final String TIPO_VENTA_CONFIRMADA = "VentaConfirmada";
    public static final String TIPO_VENTA_CANCELADA = "VentaCancelada";

    /** ms-inventario ya enlazó su cola a esta routing key. NO cambiar. */
    public static final String RK_VENTA_CREADA = "venta.creada";
    public static final String RK_VENTA_CONFIRMADA = "venta.confirmada";
    public static final String RK_VENTA_CANCELADA = "venta.cancelada";

    // ---------------------------------------------------------------------
    // Lo que ms-ventas CONSUME — copiado de ms-inventario y ms-clientes
    // ---------------------------------------------------------------------
    public static final String TIPO_PRODUCTO_CREADO = "ProductoCreado";
    public static final String TIPO_PRODUCTO_ACTUALIZADO = "ProductoActualizado";
    public static final String TIPO_PRODUCTO_DADO_DE_BAJA = "ProductoDadoDeBaja";
    public static final String TIPO_CLIENTE_CREADO = "ClienteCreado";
    public static final String TIPO_CLIENTE_ACTUALIZADO = "ClienteActualizado";
    public static final String TIPO_STOCK_RESERVADO = "StockReservado";
    public static final String TIPO_STOCK_RECHAZADO = "StockRechazado";

    /**
     * Patrón de enlace para los tres eventos de producto. En un exchange topic, {@code *} empareja exactamente
     * una palabra, y las palabras se separan por puntos: {@code dado-de-baja} es una sola palabra, así que
     * {@code producto.*} cubre {@code producto.creado}, {@code producto.actualizado} y
     * {@code producto.dado-de-baja}.
     */
    public static final String RK_PRODUCTO_TODOS = "producto.*";
    public static final String RK_CLIENTE_TODOS = "cliente.*";

    /**
     * Las dos respuestas de la saga se enlazan <b>una por una</b>, no con {@code stock.*}: ese patrón atraparía
     * también {@code stock.bajo}, que es de ms-notificaciones y que ms-ventas no debe consumir.
     */
    public static final String RK_STOCK_RESERVADO = "stock.reservado";
    public static final String RK_STOCK_RECHAZADO = "stock.rechazado";

    // ---------------------------------------------------------------------
    // Colas propias — una por agregado, cada una con su cola de fallidos
    // (RNF-17). Separarlas evita que un fallo replicando el catálogo
    // detenga la resolución de ventas, que es la ruta crítica.
    // ---------------------------------------------------------------------
    public static final String COLA_PRODUCTOS = "ventas.productos";
    public static final String COLA_PRODUCTOS_FALLIDOS = "ventas.productos.fallidos";
    public static final String COLA_CLIENTES = "ventas.clientes";
    public static final String COLA_CLIENTES_FALLIDOS = "ventas.clientes.fallidos";
    public static final String COLA_STOCK = "ventas.stock";
    public static final String COLA_STOCK_FALLIDOS = "ventas.stock.fallidos";

    // ---------------------------------------------------------------------
    // Motivos de rechazo que emite ms-inventario. Copiados de
    // InventarioEventos: son los tres valores posibles de datos.motivo
    // en StockRechazado.
    // ---------------------------------------------------------------------
    public static final String MOTIVO_PRODUCTO_NO_ENCONTRADO = "PRODUCTO_NO_ENCONTRADO";
    public static final String MOTIVO_PRODUCTO_INACTIVO = "PRODUCTO_INACTIVO";
    public static final String MOTIVO_EXISTENCIA_INSUFICIENTE = "EXISTENCIA_INSUFICIENTE";

    private VentasEventos() {
    }
}
