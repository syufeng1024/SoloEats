package com.soloeats.message.service.impl;

import com.soloeats.common.exception.BizException;
import com.soloeats.common.exception.ErrorCode;
import com.soloeats.message.domain.entity.MessageTemplate;
import com.soloeats.message.mapper.MessageTemplateMapper;
import com.soloeats.message.service.view.RenderedMessage;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageTemplateServiceImplTest {

    private static final String CODE = "ORDER_PAID";

    @Mock
    private MessageTemplateMapper messageTemplateMapper;

    private MessageTemplateServiceImpl messageTemplateService;

    @BeforeEach
    void setUp() {
        messageTemplateService = new MessageTemplateServiceImpl(messageTemplateMapper);
    }

    @Test
    @DisplayName("占位符按参数替换，标题与内容同时生效")
    void renderReplacesPlaceholders() {
        when(messageTemplateMapper.selectOne(any())).thenReturn(
                template("订单 {orderNo} 已支付", "订单 {orderNo} 实付 {amount} 元，请留意配送进度。"));

        RenderedMessage rendered = messageTemplateService.render(
                CODE, Map.of("orderNo", "SO20261025001", "amount", "35"));

        assertThat(rendered.templateCode()).isEqualTo(CODE);
        assertThat(rendered.msgType()).isEqualTo(1);
        assertThat(rendered.title()).isEqualTo("订单 SO20261025001 已支付");
        assertThat(rendered.content()).isEqualTo("订单 SO20261025001 实付 35 元，请留意配送进度。");
    }

    @Test
    @DisplayName("未提供的变量原样保留，不抛异常也不留空")
    void renderKeepsMissingPlaceholder() {
        when(messageTemplateMapper.selectOne(any())).thenReturn(
                template("订单 {orderNo}", "订单 {orderNo} 实付 {amount} 元"));

        RenderedMessage rendered = messageTemplateService.render(
                CODE, Map.of("orderNo", "SO20261025002"));

        assertThat(rendered.content()).isEqualTo("订单 SO20261025002 实付 {amount} 元");
    }

    @Test
    @DisplayName("模板不存在抛 10002")
    void renderMissingTemplateThrows() {
        when(messageTemplateMapper.selectOne(any())).thenReturn(null);

        assertBizCode(() -> messageTemplateService.render(CODE, Map.of()), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("模板已停用按不可用处理，抛 10002")
    void renderDisabledTemplateThrows() {
        MessageTemplate template = template("订单 {orderNo}", "订单 {orderNo}");
        template.setStatus(1);
        when(messageTemplateMapper.selectOne(any())).thenReturn(template);

        assertBizCode(() -> messageTemplateService.render(CODE, Map.of("orderNo", "SO1")),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    private MessageTemplate template(String titleTpl, String contentTpl) {
        MessageTemplate template = new MessageTemplate();
        template.setId(1L);
        template.setTemplateCode(CODE);
        template.setTemplateName("订单已支付");
        template.setTemplateType(1);
        template.setTitleTpl(titleTpl);
        template.setContentTpl(contentTpl);
        template.setChannels("SITE,WS");
        template.setStatus(0);
        template.setDeleted(0);
        return template;
    }

    private void assertBizCode(ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(expected.getCode());
    }
}
