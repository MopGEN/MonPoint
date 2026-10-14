package com.monpoint.clientes.event;

/**
 * Nombres que usa ms-clientes en RabbitMQ.
 * <p>
 * ⚠️ PROVISIONAL. Del catálogo de eventos (H-008, {@code docs/eventos/}) solo están decididos: los exchanges
 * ({@code clientes.eventos}, de tipo topic), los nombres de los eventos y el sobre común. Todavía NO están
 * definidos: las routing keys, el formato de {@code version} y los campos de {@code datos}. Los valores de
 * abajo son una propuesta, concentrada AQUÍ y en {@link RabbitClienteEventPublisher} para ajustarla en un solo
 * lugar cuando el Product Owner apruebe el catálogo.
 */
public final class ClienteEventos {

    public static final String EXCHANGE = "clientes.eventos";
    public static final String ORIGEN = "ms-clientes";
    /** ⚠️ Formato de versión pendiente del catálogo. */
    public static final String VERSION = "1";

    public static final String TIPO_CREADO = "ClienteCreado";
    public static final String TIPO_ACTUALIZADO = "ClienteActualizado";

    /** ⚠️ Routing keys pendientes del catálogo. */
    public static final String RK_CREADO = "cliente.creado";
    public static final String RK_ACTUALIZADO = "cliente.actualizado";

    private ClienteEventos() {
    }
}
