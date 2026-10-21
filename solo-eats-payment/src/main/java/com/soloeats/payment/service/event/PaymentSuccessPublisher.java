package com.soloeats.payment.service.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.message.OrderPaySuccessMessage;
import com.soloeats.common.message.RabbitConstants;
import com.soloeats.common.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSuccessPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(new OrderPaySuccessMessage(
                    event.orderId(), event.paymentNo(), event.tradeNo(), event.amount(),
                    TraceContext.currentTraceId()));
            rabbitTemplate.convertAndSend(
                    RabbitConstants.ORDER_EXCHANGE,
                    RabbitConstants.ROUTING_PAY_SUCCESS,
                    payload
            );
        } catch (Exception ex) {
            log.error("publish order.pay.success failed order={}", event.orderId(), ex);
        }
    }
}
