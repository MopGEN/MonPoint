package com.monpoint.inventario.event;

/**
 * Nombres que usa ms-inventario en RabbitMQ.
 * <p>
 * ⚠️ PROVISIONAL. Del catálogo de eventos (H-008, {@code docs/eventos/}) solo están decididos: los exchanges
 * ({@code inventario.eventos}, {@code ventas.eventos}, de tipo topic), los nombres de los eventos y el sobre
 * común. Todavía NO están definidos: las routing keys, los nombres de las colas, el formato de {@code version},
 * la configuración de reintentos y los campos de {@code datos}. Los valores de abajo son una propuesta,
 * concentrada AQUÍ, en {@code RabbitInventarioEventPublisher}, en {@link VentaCreadaEvento} y en
 * {@code RabbitConfig} para ajustarla en un solo lugar cuando el Product Owner apruebe el catálogo.
 */
public final class InventarioEventos {

    public static final String EXCHANGE = "inventario.eventos";
    public static final String EXCHANGE_VENTAS = "ventas.eventos";
    public static final String ORIGEN = "ms-inventario";
    /** ⚠️ Formato de versión pendiente del catálogo. */
    public static final String VERSION = "1";

    public static final String TIPO_PRODUCTO_CREADO = "ProductoCreado";
    public static final String TIPO_PRODUCTO_ACTUALIZADO = "ProductoActualizado";
    public static final String TIPO_PRODUCTO_DADO_DE_BAJA = "ProductoDadoDeBaja";
    public static final String TIPO_STOCK_RESERVADO = "StockReservado";
    public static final String TIPO_STOCK_RECHAZADO = "StockRechazado";
    public static final String TIPO_STOCK_BAJO = "StockBajo";

    /** ⚠️ Routing keys pendientes del catálogo. */
    public static final String RK_PRODUCTO_CREADO = "producto.creado";
    public static final String RK_PRODUCTO_ACTUALIZADO = "producto.actualizado";
    public static final String RK_PRODUCTO_DADO_DE_BAJA = "producto.dado-de-baja";
    public static final String RK_STOCK_RESERVADO = "stock.reservado";
    public static final String RK_STOCK_RECHAZADO = "stock.rechazado";
    public static final String RK_STOCK_BAJO = "stock.bajo";

    /** ⚠️ Routing key con la que ms-ventas publica VentaCreada: pendiente del catálogo. */
    public static final String RK_VENTA_CREADA = "venta.creada";
    /** ⚠️ Nombres de cola pendientes del catálogo. Una cola por consumidor, con su cola de fallidos (RNF-17). */
    public static final String COLA_VENTA_CREADA = "inventario.venta-creada";
    public static final String COLA_VENTA_CREADA_FALLIDOS = "inventario.venta-creada.fallidos";

    /** ⚠️ Motivos de rechazo: la documentación pide "producto y motivo" sin fijar los valores. */
    public static final String MOTIVO_PRODUCTO_NO_ENCONTRADO = "PRODUCTO_NO_ENCONTRADO";
    public static final String MOTIVO_PRODUCTO_INACTIVO = "PRODUCTO_INACTIVO";
    public static final String MOTIVO_EXISTENCIA_INSUFICIENTE = "EXISTENCIA_INSUFICIENTE";

    private InventarioEventos() {
    }
}
