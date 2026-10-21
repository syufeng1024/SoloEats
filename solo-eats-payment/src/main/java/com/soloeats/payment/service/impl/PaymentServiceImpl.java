package com.soloeats.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.common.lock.DistributedLock;
import com.soloeats.order.domain.entity.Order;
import com.soloeats.order.domain.enums.OrderStatus;
import com.soloeats.order.mapper.OrderMapper;
import com.soloeats.payment.domain.entity.PaymentOrder;
import com.soloeats.payment.domain.enums.PaymentStatus;
import com.soloeats.payment.mapper.PaymentOrderMapper;
import com.soloeats.payment.service.PaymentCallbackCommand;
import com.soloeats.payment.service.PaymentResult;
import com.soloeats.payment.service.PaymentService;
import com.soloeats.payment.service.event.PaymentSuccessEvent;
import com.soloeats.payment.util.PaymentSignUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderMapper paymentOrderMapper;
    private final OrderMapper orderMapper;
    private final DistributedLock distributedLock;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.payment.callback-secret:change_me_payment_callback_secret}")
    private String callbackSecret;

    @Override
    public PaymentResult pay(Long orderId, Integer channel, String idempotencyKey) {
        if (orderId == null || channel == null || channel < 1 || channel > 3) {
            throw new BizException(ErrorCode.PARAM_INVALID, "orderId 或 channel 不合法");
        }
        String key = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey
                : UUID.randomUUID().toString();

        PaymentOrder existing = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getIdempotencyKey, key)
                .last("LIMIT 1"));
        if (existing != null) {
            return toResult(existing);
        }

        return distributedLock.executeWithLock(
                "lock:payment:order:" + orderId,
                3,
                30,
                () -> transactionTemplate.execute(status -> doPay(orderId, channel, key))
        );
    }

    private PaymentResult doPay(Long orderId, Integer channel, String idempotencyKey) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.WAIT_PAY.getCode())) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "只有待支付订单可以发起支付");
        }
        if (order.getPayAmount() == null || order.getPayAmount() <= 0) {
            throw new BizException(ErrorCode.PAY_AMOUNT_MISMATCH);
        }

        PaymentOrder success = paymentOrderMapper.findSuccessByOrderId(orderId);
        if (success != null) {
            return toResult(success);
        }

        PaymentOrder active = paymentOrderMapper.findActiveByOrderId(orderId);
        if (active != null) {
            return toResult(active);
        }

        PaymentOrder payment = new PaymentOrder();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(orderId);
        payment.setChannel(channel);
        payment.setAmount(order.getPayAmount());
        payment.setStatus(PaymentStatus.PAYING.getCode());
        payment.setIdempotencyKey(idempotencyKey);
        try {
            paymentOrderMapper.insert(payment);
        } catch (DuplicateKeyException ex) {
            PaymentOrder duplicated = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                    .eq(PaymentOrder::getIdempotencyKey, idempotencyKey)
                    .last("LIMIT 1"));
            if (duplicated != null) {
                return toResult(duplicated);
            }
            throw new BizException(ErrorCode.PAYMENT_CONFLICT);
        }
        return toResult(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void callback(PaymentCallbackCommand command) {
        if (command == null
                || !StringUtils.hasText(command.paymentNo())
                || !StringUtils.hasText(command.tradeNo())
                || command.amount() == null
                || !StringUtils.hasText(command.sign())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "回调参数不完整");
        }
        if (!PaymentSignUtils.verify(callbackSecret, command.paymentNo(), command.tradeNo(),
                command.amount(), command.sign())) {
            throw new BizException(ErrorCode.PAY_SIGN_INVALID);
        }

        PaymentOrder payment = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getPaymentNo, command.paymentNo())
                .last("LIMIT 1"));
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!Objects.equals(payment.getAmount(), command.amount())) {
            throw new BizException(ErrorCode.PAY_AMOUNT_MISMATCH);
        }
        if (Objects.equals(payment.getStatus(), PaymentStatus.SUCCESS.getCode())) {
            return;
        }

        try {
            int rows = paymentOrderMapper.markSuccess(payment.getId(), command.tradeNo());
            if (rows == 0) {
                PaymentOrder latest = paymentOrderMapper.selectById(payment.getId());
                if (latest != null && Objects.equals(latest.getStatus(), PaymentStatus.SUCCESS.getCode())) {
                    return;
                }
                throw new BizException(ErrorCode.PAYMENT_STATE_INVALID);
            }
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.PAYMENT_CONFLICT);
        }

        eventPublisher.publishEvent(new PaymentSuccessEvent(
                payment.getOrderId(), payment.getPaymentNo(), command.tradeNo(), payment.getAmount()));
    }

    private PaymentResult toResult(PaymentOrder payment) {
        return new PaymentResult(
                payment.getId(),
                payment.getPaymentNo(),
                payment.getOrderId(),
                payment.getChannel(),
                payment.getAmount(),
                payment.getStatus()
        );
    }

    private String generatePaymentNo() {
        String time = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "PAY" + time + random;
    }
}
