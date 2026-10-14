package com.monpoint.clientes.event;

import com.monpoint.clientes.event.impl.RabbitClienteEventPublisher;
import com.monpoint.clientes.model.Cliente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitClienteEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitClienteEventPublisher publisher;

    @BeforeEach
    void preparar() {
        publisher = new RabbitClienteEventPublisher(rabbitTemplate, JsonMapper.builder().build());
    }

    @Test
    void publicarClienteCreado_EnviaElSobreComunAlExchangeDeClientes() {
        Cliente cliente = Cliente.crear("Ana Pérez", "ana@correo.com", "331", "Calle 1");

        publisher.publicarClienteCreado(cliente);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("clientes.eventos"), eq("cliente.creado"), captor.capture());
        String json = new String(captor.getValue().getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"tipo\":\"ClienteCreado\""), json);
        assertTrue(json.contains("\"origen\":\"ms-clientes\""), json);
        assertTrue(json.contains("\"eventoId\":\""), json);
        assertTrue(json.contains("\"version\":"), json);
        assertTrue(json.contains("\"fecha\":\""), json);
        assertTrue(json.contains("\"nombre\":\"Ana Pérez\""), json);
        assertEquals("application/json", captor.getValue().getMessageProperties().getContentType());
    }

    @Test
    void publicarClienteActualizado_UsaSuPropioTipoYRoutingKey() {
        publisher.publicarClienteActualizado(Cliente.crear("Ana", null, null, null));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("clientes.eventos"), eq("cliente.actualizado"), captor.capture());
        assertTrue(new String(captor.getValue().getBody(), StandardCharsets.UTF_8)
                .contains("\"tipo\":\"ClienteActualizado\""));
    }

    @Test
    void publicar_ConElBrokerCaido_NoPropagaLaExcepcion() {
        doThrow(new RuntimeException("broker caído"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        assertDoesNotThrow(() -> publisher.publicarClienteCreado(Cliente.crear("Ana", null, null, null)));
    }
}
