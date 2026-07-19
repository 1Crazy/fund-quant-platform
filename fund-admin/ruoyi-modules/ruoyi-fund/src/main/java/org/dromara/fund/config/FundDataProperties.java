package org.dromara.fund.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Python 基金数据中心调用配置。
 */
@Data
@ConfigurationProperties(prefix = "fund.data")
public class FundDataProperties {

    /** fund-quant 服务基础地址。 */
    private String providerBaseUrl;
    /** 上游 TCP 连接超时。 */
    private Duration providerConnectTimeout = Duration.ofSeconds(2);
    /** AkShare 冷缓存首次加载可能较慢。 */
    private Duration providerReadTimeout = Duration.ofSeconds(90);
    /** 名称模糊搜索单次最多同步的基金目录数量。 */
    private int searchLimit = 100;
}
