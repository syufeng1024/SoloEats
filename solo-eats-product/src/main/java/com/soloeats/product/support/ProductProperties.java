package com.soloeats.product.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 商品中心配置。价格审核开关默认关闭（P0 免审）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.product")
public class ProductProperties {

    /** 门店改价是否审核；false 时上架/改价直接写 audit_status=2。 */
    private boolean priceAuditEnabled = false;

    /** 允许的商品图外部域名前缀，逗号分隔；t_file 代理地址始终允许。 */
    private String imageHostAllowlist = "";

    /** 用户端菜单缓存 TTL（秒）。 */
    private int menuCacheSeconds = 300;
}
