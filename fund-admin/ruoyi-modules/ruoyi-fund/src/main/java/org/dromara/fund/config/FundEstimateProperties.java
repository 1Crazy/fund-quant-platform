package org.dromara.fund.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 基金实时估值部署连接配置。
 *
 * <p>运行超时、缓存和调度参数统一由 {@link FundEstimateRuntimeSettings} 从 sys_config 读取。</p>
 */
@Data
@ConfigurationProperties(prefix = "fund.estimate")
public class FundEstimateProperties {

    private String providerUrl;
}
