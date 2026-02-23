package com.busco.localizacao_core_service.infra.rabbit;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "tracking.exchange";

    @Bean
    public TopicExchange trackingExchange() {
        return new TopicExchange(EXCHANGE);
    }

}