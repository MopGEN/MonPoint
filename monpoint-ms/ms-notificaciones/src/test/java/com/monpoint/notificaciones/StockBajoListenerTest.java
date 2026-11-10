package com.monpoint.notificaciones;

import com.monpoint.notificaciones.event.StockBajo;
import com.monpoint.notificaciones.listener.StockBajoListener;
import com.monpoint.notificaciones.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockBajoListenerTest {
    private final NotificacionService servicio = mock(NotificacionService.class);
    private final StockBajoListener listener = new StockBajoListener(JsonMapper.builder().build(), servicio);
    private static final String SOBRE = """
            {"eventoId":"evento-1","tipo":"StockBajo","version":"1","fecha":"2026-11-10T12:00:00Z",
             "origen":"ms-inventario","datos":{"productoId":"507f1f77bcf86cd799439011",
             "nombre":"Tinta","existenciaResultante":5}}
            """;
    @Test
    void leeElNombreRealDelContratoYConservaIdentidad() {
        listener.recibir(mensaje(SOBRE, "evento-1"));
        verify(servicio).recibir(new StockBajo("evento-1", "StockBajo", "1", "2026-11-10T12:00:00Z",
                "ms-inventario", new StockBajo.Datos("507f1f77bcf86cd799439011", "Tinta", 5)));
    }
    @Test
    void messageIdDiscordanteSeRechazaAntesDePersistir() {
        assertThatThrownBy(() -> listener.recibir(mensaje(SOBRE, "otro"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(servicio);
    }
    @ParameterizedTest
    @ValueSource(strings = {"null", "{malformado", "[]"})
    void mensajeIlegibleNoLlegaAlServicio(String contenido) {
        assertThatThrownBy(() -> listener.recibir(mensaje(contenido, "evento-1"))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(servicio);
    }
    @ParameterizedTest
    @ValueSource(strings = {"tipo", "version", "fecha", "origen", "datos", "eventoId"})
    void sobreIncompletoNoAbreTransaccion(String campo) {
        var mongo = mock(MongoTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        var real = new NotificacionService(mongo, manager);
        var json = JsonMapper.builder().build();
        var arbol = (tools.jackson.databind.node.ObjectNode) json.readTree(SOBRE);
        arbol.remove(campo);
        assertThatThrownBy(() -> real.recibir(json.treeToValue(arbol, StockBajo.class))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(mongo, manager);
    }
    @ParameterizedTest
    @ValueSource(strings = {"productoId", "nombre", "existenciaResultante"})
    void datosIncompletosNoAbrenTransaccion(String campo) {
        var mongo = mock(MongoTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        var real = new NotificacionService(mongo, manager);
        var json = JsonMapper.builder().build();
        var arbol = (tools.jackson.databind.node.ObjectNode) json.readTree(SOBRE);
        ((tools.jackson.databind.node.ObjectNode) arbol.get("datos")).remove(campo);
        assertThatThrownBy(() -> real.recibir(json.treeToValue(arbol, StockBajo.class))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(mongo, manager);
    }
    private static Message mensaje(String cuerpo, String id) {
        var propiedades = new MessageProperties();
        propiedades.setMessageId(id);
        return new Message(cuerpo.getBytes(StandardCharsets.UTF_8), propiedades);
    }
}
