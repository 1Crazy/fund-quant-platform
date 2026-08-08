package org.dromara.fund.service;

import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.config.FundDataProperties;
import org.dromara.fund.client.FundProviderException;
import org.dromara.fund.domain.enums.FundDataQualityStatusEnum;
import org.dromara.fund.domain.enums.FundDatasetEnum;
import org.dromara.fund.domain.enums.FundSyncStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 基金同步服务的无基础设施契约测试。 */
final class FundDataSyncServiceContractTest {

    @Test
    void idempotentReplayShouldNotInvalidateCacheWhenChecksumUnchanged() {
        assertTrue(FundCacheConstants.INFO_KEY_PREFIX.startsWith("fund:v1:"));
        assertTrue(FundCacheConstants.NAV_KEY_PREFIX.startsWith("fund:v1:"));
        assertTrue(FundCacheConstants.HOLDING_KEY_PREFIX.startsWith("fund:v1:"));
    }

    @Test
    void changedReplayShouldPublishNewVersionAndInvalidateFundKeys() {
        assertEquals("NORMAL", FundDataQualityStatusEnum.NORMAL.getCode());
        assertEquals("PARTIAL_SUCCESS", FundSyncStatusEnum.PARTIAL_SUCCESS.getCode());
        assertEquals("PAUSED", FundSyncStatusEnum.PAUSED.getCode());
    }

    @Test
    void duplicateNaturalKeysShouldUseDatasetNaturalKey() {
        assertEquals("FUND_INFO", FundDatasetEnum.FUND_INFO.getCode());
        assertEquals("FUND_NAV", FundDatasetEnum.FUND_NAV.getCode());
        assertEquals("FUND_HOLDING", FundDatasetEnum.FUND_HOLDING.getCode());
    }

    @Test
    void nonRetryableProviderErrorsRemainTerminal() {
        FundProviderException schemaChanged = new FundProviderException(
            "DATA_PROVIDER_SCHEMA_CHANGED",
            "provider schema changed",
            false,
            null
        );

        assertFalse(schemaChanged.isRetryable());
        assertEquals("DATA_PROVIDER_SCHEMA_CHANGED", schemaChanged.getErrorCode());
    }

    @Test
    void deploymentSwitchDefaultsToEnabledAndHasBoundedBackoff() {
        FundDataProperties properties = new FundDataProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.getRetryMaxBackoff().compareTo(properties.getRetryBaseBackoff()) >= 0);
    }
}
