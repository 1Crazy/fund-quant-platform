package org.dromara.fund.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 历史 NAV 位置计算上游地址。
 *
 * <p>仅保存部署地址。窗口、阈值和精度等计算语义由已锁定的量化配置发布提供。</p>
 */
@Data
@ConfigurationProperties(prefix = "fund.nav-position")
public class FundNavPositionProperties {

    private String providerUrl;
}
