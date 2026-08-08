package org.dromara.fund.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 基金估值模块配置入口。
 */
@AutoConfiguration
@EnableConfigurationProperties({
    FundEstimateProperties.class,
    FundDataProperties.class,
    FundNavPositionProperties.class,
    QuantConfigProperties.class
})
@EnableScheduling
public class FundEstimateConfig {
}
