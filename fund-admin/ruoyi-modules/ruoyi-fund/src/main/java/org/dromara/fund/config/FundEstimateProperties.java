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
    private Duration cacheTtl = Duration.ofSeconds(45);
    private Duration staleAfter = Duration.ofMinutes(3);
    private String zoneId = "Asia/Shanghai";
}
