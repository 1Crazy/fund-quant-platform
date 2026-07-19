package org.dromara.fund.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * fund-quant 历史净值响应。
 */
@Data
public class FundNavProviderResponse {

    @JsonAlias("fund_code")
    private String fundCode;
    @JsonAlias("navDate")
    private LocalDate date;
    @JsonAlias({"unitNav", "nav"})
    private BigDecimal nav;
    @JsonAlias({"accumulated_nav", "accumulatedNav"})
    private BigDecimal accumulatedNav;
    @JsonAlias({"growth_rate", "dailyReturn", "dailyGrowthRate"})
    private BigDecimal growthRate;
    private String source;
    @JsonAlias({"source_time", "sourceUpdatedAt"})
    private OffsetDateTime sourceTime;
    @JsonAlias("quality_status")
    private String qualityStatus;
    private String checksum;
    @JsonAlias("data_version")
    private String dataVersion;
}
