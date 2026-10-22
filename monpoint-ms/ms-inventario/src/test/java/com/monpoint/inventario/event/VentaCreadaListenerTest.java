package com.monpoint.inventario.event;

import com.monpoint.inventario.service.ReservaStockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VentaCreadaListenerTest {

    @Mock
    private ReservaStockService reservaStockService;

    private VentaCreadaListener listener;

    @BeforeEach
    void preparar() {
        listener = new VentaCreadaListener(JsonMapper.builder().build(), reservaStockService);
    }

    private static Message mensaje(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void alRecibir_ConVentaCreadaValida_DelegaEnElServicioConLasLineas() {
        listener.alRecibir(mensaje("""
                {"eventoId":"ev-1","tipo":"VentaCreada","version":"1","fecha":"2026-10-13T10:00:00Z",
                 "origen":"ms-ventas",
                 "datos":{"ventaId":"v-1","campoNuevo":"se ignora",
                          "lineas":[{"productoId":"p1","cantidad":3},{"productoId":"p2","cantidad":1}]}}
                """));

        ArgumentCaptor<VentaCreadaEvento> captor = ArgumentCaptor.forClass(VentaCreadaEvento.class);
        verify(reservaStockService).procesarVentaCreada(captor.capture());
        VentaCreadaEvento evento = captor.getValue();
        assertEquals("ev-1", evento.eventoId());
        assertEquals("v-1", evento.datos().ventaId());
        assertEquals(2, evento.datos().lineas().size());
        assertEquals("p1", evento.datos().lineas().get(0).productoId());
        assertEquals(3, evento.datos().lineas().get(0).cantidad());
    }

    @Test
    void alRecibir_ConMensajeQueNoEsJson_PropagaLaExcepcionParaElReintento() {
        assertThrows(RuntimeException.class, () -> listener.alRecibir(mensaje("esto no es json")));

        verify(reservaStockService, never()).procesarVentaCreada(any());
    }
}
