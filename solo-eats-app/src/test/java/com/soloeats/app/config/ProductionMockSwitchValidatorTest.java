package com.soloeats.app.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 生产环境模拟通道启动校验（规格书 9.3）：prod profile 下任一模拟开关为 true 必须启动失败。
 */
class ProductionMockSwitchValidatorTest {

    @Test
    @DisplayName("非生产 profile 一律放行，保持本地开发体验")
    void nonProductionProfileAllowsMockSwitches() {
        ProductionMockSwitchValidator validator = validator(environment("local"), true, true, true, "mock");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("未激活 profile 时放行")
    void emptyProfileAllowsMockSwitches() {
        MockEnvironment environment = new MockEnvironment();
        ProductionMockSwitchValidator validator = validator(environment, true, true, true, "");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod 下模拟登录开启时启动失败")
    void prodRejectsMockLogin() {
        ProductionMockSwitchValidator validator = validator(environment("prod"), true, false, false, "");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.mock-login-enabled");
    }

    @Test
    @DisplayName("prod 下模拟短信开启时启动失败")
    void prodRejectsMockSms() {
        ProductionMockSwitchValidator validator = validator(environment("prod"), false, true, false, "");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.auth.mock-sms-enabled");
    }

    @Test
    @DisplayName("prod 下模拟充值开启时启动失败")
    void prodRejectsMockRecharge() {
        ProductionMockSwitchValidator validator = validator(environment("production"), false, false, true, "");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.wallet.mock-recharge-enabled");
    }

    @Test
    @DisplayName("prod 下模拟支付网关开启时启动失败")
    void prodRejectsMockPaymentGateway() {
        ProductionMockSwitchValidator validator = validator(environment("prod"), false, false, false, "mock");

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.payment.gateway");
    }

    @Test
    @DisplayName("prod 下全部模拟通道关闭时正常启动")
    void prodWithAllMockSwitchesClosedStarts() {
        ProductionMockSwitchValidator validator = validator(environment("prod"), false, false, false, "alipay");

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }

    private ProductionMockSwitchValidator validator(MockEnvironment environment,
                                                    boolean mockLoginEnabled,
                                                    boolean mockSmsEnabled,
                                                    boolean mockRechargeEnabled,
                                                    String paymentGateway) {
        return new ProductionMockSwitchValidator(environment, mockLoginEnabled, mockSmsEnabled,
                mockRechargeEnabled, paymentGateway);
    }
}
