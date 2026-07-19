package org.dromara.fund.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper SQL 契约，防止自然键或条件更新被后续改动移除。
 */
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
