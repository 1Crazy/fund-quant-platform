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
    /** 批量目录同步页大小。 */
    private int catalogPageSize = 200;
    /** 数据供应方每分钟速率限制。 */
    private int providerRatePerMinute = 60;
    /** 可重试上游错误最大尝试次数。 */
    private int maxRetryAttempts = 3;
    /** 指数退避基础间隔。 */
    private Duration retryBaseBackoff = Duration.ofSeconds(1);
    /** 指数退避最大间隔。 */
    private Duration retryMaxBackoff = Duration.ofMinutes(2);
    /** 同步状态摘要缓存 TTL。 */
    private Duration syncStatusCacheTtl = Duration.ofSeconds(30);
    /** 基金读模型缓存 TTL。 */
    private Duration infoCacheTtl = Duration.ofMinutes(30);
    /** 净值序列缓存 TTL。 */
    private Duration navCacheTtl = Duration.ofHours(1);
    /** 持仓缓存 TTL。 */
    private Duration holdingCacheTtl = Duration.ofHours(6);
    /** 是否启用定时增量入口。 */
    private boolean scheduleEnabled = false;
    /** 数据中心同步总开关；关闭后只读取最后成功版本。 */
    private boolean enabled = true;
    /** 日常增量同步向前覆盖的自然日数量。 */
    private int incrementalNavDays = 14;
}
