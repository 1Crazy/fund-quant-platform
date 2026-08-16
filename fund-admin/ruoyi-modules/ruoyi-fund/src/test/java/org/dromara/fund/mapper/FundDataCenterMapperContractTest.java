package org.dromara.fund.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper SQL 契约，防止自然键或条件更新被后续改动移除。
 */
@Tag("dev")
final class FundDataCenterMapperContractTest {

    @Test
    void upsertSqlShouldUsePostgresNaturalKeys() {
        String fundInfo = mapper("mapper/fund/FundInfoMapper.xml");
        String nav = mapper("mapper/fund/FundNavMapper.xml");
        String holding = mapper("mapper/fund/FundHoldingMapper.xml");

        assertTrue(fundInfo.contains("ON CONFLICT (fund_code)"));
        assertTrue(nav.contains("ON CONFLICT (fund_code, nav_date)"));
        assertTrue(holding.contains("ON CONFLICT (fund_code, report_date, stock_code)"));
    }

    @Test
    void qualityIssueShouldKeepFailureRecordForDuplicateReplays() {
        String issue = mapper("mapper/fund/FundDataQualityIssueMapper.xml");
        assertTrue(issue.contains("selectRecentByFundCode"));
        assertTrue(issue.contains("record_key LIKE"));
    }

    @Test
    void quantConfigMapperShouldRetainOptimisticLockAndReleaseLocking() {
        String version = mapper("mapper/fund/QuantConfigVersionMapper.xml");
        String release = mapper("mapper/fund/QuantConfigReleaseMapper.xml");

        assertTrue(version.contains("revision = #{expectedRevision}"));
        assertTrue(version.contains("status = 'DRAFT'"));
        assertTrue(version.contains("FOR UPDATE"));
        assertTrue(release.contains("nextval('quant_config_release_version_seq')"));
    }

    @Test
    void estimateQueriesMustUseExactQuantConfigReleaseLineage() {
        String fundInfo = mapper("mapper/fund/FundInfoMapper.xml");
        String estimate = mapper("mapper/fund/FundEstimateMapper.xml");

        assertTrue(fundInfo.contains("fe.config_release_version = #{configReleaseVersion}"));
        assertTrue(fundInfo.contains("fe.config_release_checksum = #{configReleaseChecksum}"));
        assertTrue(estimate.contains("config_release_version = #{configReleaseVersion}"));
        assertTrue(estimate.contains("config_release_checksum = #{configReleaseChecksum}"));
    }

    @Test
    void estimateSelectionMustUseConfiguredFreshnessAndRejectAnyBadLatestHolding() {
        String fundInfo = mapper("mapper/fund/FundInfoMapper.xml");

        assertTrue(fundInfo.contains("#{estimateStaleAfterSeconds} * INTERVAL '1 second'"));
        assertFalse(fundInfo.contains("INTERVAL '3 minutes'"));
        assertTrue(fundInfo.contains("AND NOT EXISTS ("));
        assertTrue(fundInfo.contains("holding.quality_status IS DISTINCT FROM 'NORMAL'"));
    }

    @Test
    void estimateBatchRecalculationMustUseStableShardAndCursorPredicates() {
        String fundInfo = mapper("mapper/fund/FundInfoMapper.xml");

        assertTrue(fundInfo.contains("selectReadyEstimateFundCodesForShard"));
        assertTrue(fundInfo.contains("hashtext(fi.fund_code)"));
        assertTrue(fundInfo.contains("fi.fund_code &gt; #{lastFundCode}"));
    }

    @Test
    void fundNameFilterMustGivePostgresAnExplicitParameterType() {
        String fundInfo = mapper("mapper/fund/FundInfoMapper.xml");

        assertTrue(fundInfo.contains("CAST(#{bo.fundName} AS varchar)"));
        assertFalse(fundInfo.contains("CONCAT('%', #{bo.fundName}, '%')"));
    }

    @Test
    void fundTypeFilterMustMatchLocalSubtypeValues() {
        String fundInfo = mapper("mapper/fund/FundInfoMapper.xml");

        assertTrue(fundInfo.contains("fi.fund_type LIKE (CAST(#{bo.fundType} AS varchar) || '%')"));
        assertFalse(fundInfo.contains("fi.fund_type = #{bo.fundType}"));
    }

    private String mapper(String resource) {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("缺少 Mapper 资源: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Mapper 资源失败", e);
        }
    }
}
