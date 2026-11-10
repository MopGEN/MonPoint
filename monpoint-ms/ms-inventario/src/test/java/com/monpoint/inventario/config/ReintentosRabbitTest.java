package com.monpoint.inventario.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Carga application.properties real y el interceptor que configura Boot; no requiere Mongo ni broker. */
class ReintentosRabbitTest {
    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class));

    /** Misma posición del Message que invokeListener(Channel, Message) del contenedor AMQP. */
    public interface Entrega { void recibir(Object canal, Message mensaje); }

    @Test
    void falloPersistenteProduceTresEntregasYRechazoSinReencolar() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var intentos = new AtomicInteger();
            Entrega falla = (canal, mensaje) -> {
                intentos.incrementAndGet();
                throw new IllegalStateException("Fallo persistente inducido");
            };
            var entrega = conInterceptor(ctx.getBean(SimpleRabbitListenerContainerFactory.class), falla);
            assertThatThrownBy(() -> entrega.recibir(null, new Message(new byte[0])))
                    .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
            assertThat(intentos).as("entrega inicial más dos reintentos").hasValue(3);
        });
    }

    @Test
    void falloTransitorioQueSeRecuperaEnLaTerceraEntregaNoSeRechaza() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            var intentos = new AtomicInteger();
            Entrega transitoria = (canal, mensaje) -> {
                if (intentos.incrementAndGet() < 3) throw new IllegalStateException("Fallo transitorio inducido");
            };
            var entrega = conInterceptor(ctx.getBean(SimpleRabbitListenerContainerFactory.class), transitoria);
            assertThatCode(() -> entrega.recibir(null, new Message(new byte[0]))).doesNotThrowAnyException();
            assertThat(intentos).hasValue(3);
        });
    }

    private static Entrega conInterceptor(SimpleRabbitListenerContainerFactory factory, Entrega destino) {
        assertThat(factory.getAdviceChain()).as("reintentos habilitados por la configuración del servicio").isNotEmpty();
        var proxy = new ProxyFactory(destino);
        for (var advice : factory.getAdviceChain()) proxy.addAdvice(advice);
        return (Entrega) proxy.getProxy();
    }
}
