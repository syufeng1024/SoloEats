package com.soloeats.order.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.message.OrderTimeoutMessage;
import com.soloeats.common.message.RabbitConstants;
import com.soloeats.common.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.order.timeout-delay-seconds:900}")
    private long delaySeconds;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(
                    new OrderTimeoutMessage(event.orderId(), TraceContext.currentTraceId()));
            rabbitTemplate.convertAndSend(
                    RabbitConstants.ORDER_EXCHANGE,
                    RabbitConstants.ROUTING_DELAY,
                    payload,
                    message -> {
                        message.getMessageProperties().setExpiration(String.valueOf(delaySeconds * 1000L));
                        return message;
                    }
            );
        } catch (Exception ex) {
            log.error("publish order timeout message failed order={}", event.orderId(), ex);
        }
    }
}
