package com.soloeats.app.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 生产环境模拟通道启动校验（规格书 9.3）。
 *
 * <p>规格要求「模拟支付 / 模拟短信的开关必须在生产环境强制关闭（启动配置校验），
 * 避免"一键支付成功"误入正式环境」。本校验在 Bean 初始化阶段（早于对外提供服务的端口监听）
 * 读取激活 profile，一旦检测到 {@code prod} / {@code production} 且以下任一开关为 true，
 * 直接抛异常使 Spring 上下文启动失败：
 * <ul>
 *   <li>{@code app.auth.mock-login-enabled}：旧契约模拟登录</li>
 *   <li>{@code app.auth.mock-sms-enabled}：短信验证码固定 000000</li>
 *   <li>{@code app.wallet.mock-recharge-enabled}：C 端模拟充值</li>
 *   <li>{@code app.payment.gateway=mock}：模拟支付网关（仅在显式配置时校验）</li>
 * </ul>
 *
 * <p>非生产 profile（local / dev / test）不做任何校验，保持本地开发体验不变。
 * 异常使用 {@link IllegalStateException}：这是启动期配置自检，不属于对外业务异常，
 * 不应进入 {@code GlobalExceptionHandler} 的 BizException 处理链路。
 */
@Component
public class ProductionMockSwitchValidator implements InitializingBean {

    /** 视为生产环境的 profile 名。 */
    private static final List<String> PRODUCTION_PROFILES = List.of("prod", "production");

    /** 显式声明使用模拟支付网关时的取值。 */
    private static final String MOCK_PAYMENT_GATEWAY = "mock";

    private final Environment environment;
    private final boolean mockLoginEnabled;
    private final boolean mockSmsEnabled;
    private final boolean mockRechargeEnabled;
    private final String paymentGatewayMode;

    public ProductionMockSwitchValidator(
            Environment environment,
            @Value("${app.auth.mock-login-enabled:true}") boolean mockLoginEnabled,
            @Value("${app.auth.mock-sms-enabled:true}") boolean mockSmsEnabled,
            @Value("${app.wallet.mock-recharge-enabled:true}") boolean mockRechargeEnabled,
            @Value("${app.payment.gateway:}") String paymentGatewayMode) {
        this.environment = environment;
        this.mockLoginEnabled = mockLoginEnabled;
        this.mockSmsEnabled = mockSmsEnabled;
        this.mockRechargeEnabled = mockRechargeEnabled;
        this.paymentGatewayMode = paymentGatewayMode;
    }

    @Override
    public void afterPropertiesSet() {
        if (!isProductionProfile()) {
            return;
        }
        assertSwitchClosed("app.auth.mock-login-enabled", mockLoginEnabled);
        assertSwitchClosed("app.auth.mock-sms-enabled", mockSmsEnabled);
        assertSwitchClosed("app.wallet.mock-recharge-enabled", mockRechargeEnabled);
        if (StringUtils.hasText(paymentGatewayMode)
                && MOCK_PAYMENT_GATEWAY.equalsIgnoreCase(paymentGatewayMode.trim())) {
            throw new IllegalStateException(
                    "生产环境禁止使用模拟支付通道：app.payment.gateway=mock，请切换到真实支付网关");
        }
    }

    /**
     * 判断当前是否为生产环境。
     *
     * <p>未显式激活任何 profile 时按非生产处理：Spring Boot 默认 profile 为 {@code default}，
     * 强制失败会让本地直接 `spring-boot:run` 的场景全部起不来。
     */
    private boolean isProductionProfile() {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles == null || activeProfiles.length == 0) {
            return false;
        }
        return Arrays.stream(activeProfiles)
                .filter(StringUtils::hasText)
                .map(profile -> profile.trim().toLowerCase())
                .anyMatch(PRODUCTION_PROFILES::contains);
    }

    private void assertSwitchClosed(String property, boolean enabled) {
        if (enabled) {
            throw new IllegalStateException(
                    "生产环境禁止开启模拟通道：" + property + "=true，请通过环境变量显式关闭后重启");
        }
    }
}
