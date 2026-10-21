package com.soloeats.app.config;

import com.soloeats.common.message.RabbitConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(RabbitConstants.ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(RabbitConstants.ORDER_DELAY_QUEUE)
                .deadLetterExchange(RabbitConstants.ORDER_EXCHANGE)
                .deadLetterRoutingKey(RabbitConstants.ROUTING_TIMEOUT)
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return QueueBuilder.durable(RabbitConstants.ORDER_TIMEOUT_QUEUE).build();
    }

    @Bean
    public Queue orderPaySuccessQueue() {
        return QueueBuilder.durable(RabbitConstants.ORDER_PAY_SUCCESS_QUEUE).build();
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderExchange())
                .with(RabbitConstants.ROUTING_DELAY);
    }

    @Bean
    public Binding orderTimeoutBinding() {
        return BindingBuilder.bind(orderTimeoutQueue())
                .to(orderExchange())
                .with(RabbitConstants.ROUTING_TIMEOUT);
    }

    @Bean
    public Binding orderPaySuccessBinding() {
        return BindingBuilder.bind(orderPaySuccessQueue())
                .to(orderExchange())
                .with(RabbitConstants.ROUTING_PAY_SUCCESS);
    }
}
