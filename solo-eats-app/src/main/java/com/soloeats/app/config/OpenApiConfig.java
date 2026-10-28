package com.soloeats.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 文档元信息（Swagger UI）。
 *
 * <p>访问入口：
 * <ul>
 *   <li>Swagger UI：{@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>OpenAPI JSON：{@code http://localhost:8080/v3/api-docs}（可导入 Apifox / Postman / YApi）</li>
 * </ul>
 *
 * <p><b>鉴权说明</b>：项目业务接口统一由 {@code JwtAuthFilter} + {@code /api/**} 拦截器保护，
 * 在 Swagger UI 里点右上角 Authorize 填入登录接口返回的 token 即可调试受保护接口。
 * 公开接口（登录、门店列表等）不填 token 也能直接调用。
 *
 * <p>整个配置类由 {@code springdoc.api-docs.enabled} 控制是否加载：生产环境通过
 * {@code API_DOC_ENABLED=false} 关闭后，本类与 springdoc 自动配置都不会生效。
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {

    /** Swagger UI 里 Authorize 按钮对应的安全方案名。 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    private final String applicationVersion;

    public OpenApiConfig(@Value("${app.api-doc.version:1.0.0-SNAPSHOT}") String applicationVersion) {
        this.applicationVersion = applicationVersion;
    }

    @Bean
    public OpenAPI soloEatsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("数创星光外卖 SoloEats API")
                        .version(applicationVersion)
                        .description("""
                                本地/测试环境的接口文档，覆盖 C 端、骑手端、商家端与运营后台四类接口。

                                路径约定：
                                - /api/v1/c/**          C 端（消费者）
                                - /api/v1/rider/**      骑手端
                                - /api/v1/merchant/**   商家端
                                - /api/v1/admin/**      运营后台

                                统一响应体：{code, message, data, traceId}，code=0 表示成功。

                                鉴权：点击右上角 Authorize，粘贴登录接口返回的 token 原文即可，
                                无需手动加 "Bearer " 前缀。""")
                        .contact(new io.swagger.v3.oas.models.info.Contact()
                                .name("SoloEats 开发团队")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("POST /api/v1/auth/login 返回的 token 字段值")))
                // 全局声明使用该方案：Swagger UI 会为每个接口提供 Authorize 入口，
                // 实际是否必须带 token 仍由后端拦截器决定，公开接口留空即可调用。
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /*
     * 按端分组：全量 307 个路径一次性渲染时 Swagger UI 首屏要等约 30s，
     * 拆成 5 个分组后每个文档只加载自己那一部分，首屏能压到几秒内。
     * 分组后 Swagger UI 顶部会出现下拉框，也可直接用 /v3/api-docs/{group} 取单个分组的 JSON。
     */

    @Bean
    public GroupedOpenApi consumerApi() {
        return GroupedOpenApi.builder()
                .group("c")
                .displayName("C 端（消费者）")
                .pathsToMatch("/api/v1/c/**")
                .build();
    }

    @Bean
    public GroupedOpenApi riderApi() {
        return GroupedOpenApi.builder()
                .group("rider")
                .displayName("骑手端")
                .pathsToMatch("/api/v1/rider/**")
                .build();
    }

    @Bean
    public GroupedOpenApi merchantApi() {
        return GroupedOpenApi.builder()
                .group("merchant")
                .displayName("商家端")
                .pathsToMatch("/api/v1/merchant/**")
                .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("运营后台")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }

    /**
     * 公共接口：登录、订单、支付、消息、文件、通知等不归属单一端的接口，外加健康探针。
     * 用排除法而非逐个枚举，避免以后新增公共接口时漏配。
     */
    @Bean
    public GroupedOpenApi commonApi() {
        return GroupedOpenApi.builder()
                .group("common")
                .displayName("公共接口（登录/订单/支付/消息/文件）")
                .pathsToMatch("/api/**", "/readyz", "/healthz")
                .pathsToExclude("/api/v1/c/**", "/api/v1/rider/**",
                        "/api/v1/merchant/**", "/api/v1/admin/**")
                .build();
    }
}
