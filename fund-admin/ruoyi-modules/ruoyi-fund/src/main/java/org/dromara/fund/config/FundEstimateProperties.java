package org.dromara.fund.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 基金实时估值配置。
 */
@Data
@ConfigurationProperties(prefix = "fund.estimate")
public class FundEstimateProperties {

    private String providerUrl;
    /** 上游 TCP 连接超时。 */
    private Duration providerConnectTimeout = Duration.ofSeconds(2);
    /** AkShare 冷缓存需要抓取多个公开数据集，读取超时应覆盖首次加载。 */
    private Duration providerReadTimeout = Duration.ofSeconds(30);
    private Duration cacheTtl = Duration.ofSeconds(45);
    private Duration staleAfter = Duration.ofMinutes(3);
    private String zoneId = "Asia/Shanghai";
}
