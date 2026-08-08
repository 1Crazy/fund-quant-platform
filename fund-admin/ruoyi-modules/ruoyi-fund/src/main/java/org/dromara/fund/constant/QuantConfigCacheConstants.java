package org.dromara.fund.constant;

/** Redis 中的量化配置只读投影键。 */
public interface QuantConfigCacheConstants {
    String ACTIVE_RELEASE_KEY = "fund:quant-config:release:active";
    String RELEASE_KEY_PREFIX = "fund:quant-config:release:";
    String GROUP_KEY_PREFIX = "fund:quant-config:group:";
    String INVALIDATE_CHANNEL = "fund:quant-config:invalidate";
}
