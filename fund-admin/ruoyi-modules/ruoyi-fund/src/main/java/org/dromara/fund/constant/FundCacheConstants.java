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

    /** 多节点调度防重锁。 */
    public static final String ESTIMATE_SCHEDULE_LOCK = "fund:lock:estimate:schedule";
}
