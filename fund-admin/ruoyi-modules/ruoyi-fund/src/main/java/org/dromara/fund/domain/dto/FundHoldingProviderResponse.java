package org.dromara.fund.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * fund-quant 基金股票持仓响应。
 */
@Data
public class FundHoldingProviderResponse {

    /** 基金代码。 */
    @JsonAlias("fund_code")
    private String fundCode;
    /** 股票代码。 */
    @JsonAlias("stock_code")
    private String stockCode;
    /** 股票名称。 */
    @JsonAlias("stock_name")
    private String stockName;
    /** 占基金净值比例，百分数口径。 */
    @JsonAlias({"disclosedWeight", "holdingRatio"})
    private BigDecimal weight;
    /** 公开披露报告期。 */
    @JsonAlias("report_period")
    private String reportPeriod;
    @JsonAlias("report_date")
    private LocalDate reportDate;
    @JsonAlias({"holdingRank", "rankNo"})
    private Integer rank;
    private String source;
    @JsonAlias({"source_time", "sourceUpdatedAt"})
    private OffsetDateTime sourceTime;
    @JsonAlias("quality_status")
    private String qualityStatus;
    private String checksum;
    @JsonAlias("data_version")
    private String dataVersion;
}
