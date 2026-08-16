package org.dromara.fund.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 基金缓存键。
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FundCacheConstants {

    /** 单基金估值热点缓存前缀。 */
    public static final String ESTIMATE_KEY_PREFIX = "fund:estimate:";

    /** 单基金回源防击穿锁前缀。 */
    public static final String ESTIMATE_LOCK_PREFIX = "fund:lock:estimate:";

    public static String estimateCacheKey(
        String fundCode,
        String algorithmVersion,
        Long releaseVersion,
        String releaseChecksum
    ) {
        return ESTIMATE_KEY_PREFIX + fundCode + ":" + algorithmVersion + ":" + releaseVersion + ":" + releaseChecksum;
    }

    public static String estimateCachePattern(String fundCode) {
        return ESTIMATE_KEY_PREFIX + fundCode + ":*";
    }

    public static String estimateLockKey(String fundCode) {
        return ESTIMATE_LOCK_PREFIX + fundCode;
    }

    /** 多节点调度防重锁。 */
    public static final String ESTIMATE_SCHEDULE_LOCK = "fund:lock:estimate:schedule";

    /** 全量历史位置计算状态与多节点防重锁。 */
    public static final String NAV_POSITION_BATCH_STATUS_KEY = "fund:nav-position:batch:status";
    public static final String NAV_POSITION_BATCH_LOCK = "fund:lock:nav-position:batch";

    /** 基金数据中心缓存版本前缀。 */
    public static final String DATA_CENTER_VERSION_PREFIX = "fund:v1:";

    /** 基金目录/搜索缓存前缀。 */
    public static final String CATALOG_KEY_PREFIX = DATA_CENTER_VERSION_PREFIX + "catalog:";

    /** 单基金详情缓存前缀。 */
    public static final String INFO_KEY_PREFIX = DATA_CENTER_VERSION_PREFIX + "info:";

    /** 基金净值序列缓存前缀。 */
    public static final String NAV_KEY_PREFIX = DATA_CENTER_VERSION_PREFIX + "nav:";

    /** 基金最新披露持仓缓存前缀。 */
    public static final String HOLDING_KEY_PREFIX = DATA_CENTER_VERSION_PREFIX + "holding:";

    /** 同步状态摘要缓存前缀。 */
    public static final String SYNC_STATUS_KEY_PREFIX = DATA_CENTER_VERSION_PREFIX + "sync:status:";

    /** 单基金同步锁前缀。 */
    public static final String SYNC_FUND_LOCK_PREFIX = DATA_CENTER_VERSION_PREFIX + "lock:sync:fund:";

    /** 全局同步锁前缀。 */
    public static final String SYNC_GLOBAL_LOCK_PREFIX = DATA_CENTER_VERSION_PREFIX + "lock:sync:global:";

    /** 数据供应方限流器。 */
    public static final String PROVIDER_RATE_LIMIT_KEY = DATA_CENTER_VERSION_PREFIX + "rate:provider";
}
