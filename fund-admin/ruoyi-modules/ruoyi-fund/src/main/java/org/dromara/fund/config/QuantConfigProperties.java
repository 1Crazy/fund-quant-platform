package org.dromara.fund.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 配置中心的连接、缓存和门禁等运维参数；不承载任何数学模型值。 */
@Data
@ConfigurationProperties(prefix = "fund.quant-config")
public class QuantConfigProperties {
    private String providerValidationUrl;
    private Duration providerConnectTimeout = Duration.ofSeconds(2);
    private Duration providerReadTimeout = Duration.ofSeconds(10);
    private Duration groupCacheTtl = Duration.ofHours(24);
    /** D-011 未记录为已采纳前，禁止创建第一条发布记录。 */
    private boolean initialReleaseApproved;
}
