package com.soloeats.order.service.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soloeats.common.message.OrderPaySuccessMessage;
import com.soloeats.common.message.RabbitConstants;
import com.soloeats.common.trace.TraceContext;
import com.soloeats.order.service.OrderPaymentApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaySuccessConsumer {

    private final ObjectMapper objectMapper;
    private final OrderPaymentApplicationService orderPaymentApplicationService;

    @RabbitListener(queues = RabbitConstants.ORDER_PAY_SUCCESS_QUEUE)
    public void onMessage(String payload) throws Exception {
        OrderPaySuccessMessage message = objectMapper.readValue(payload, OrderPaySuccessMessage.class);
        try (TraceContext.TraceScope ignored = TraceContext.open(message.traceId())) {
            log.info("consume order.pay.success order={}", message.orderId());
            orderPaymentApplicationService.handlePaySuccess(message.orderId());
        }
    }
}
