package org.dromara.fund.domain.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * fund-quant 基金基础信息响应。
 */
@Data
public class FundProviderResponse {

    @JsonAlias("fund_code")
    private String fundCode;
    @JsonAlias("fund_name")
    private String fundName;
    @JsonAlias("fund_type")
    private String fundType;
    @JsonAlias("pinyin_abbr")
    private String pinyinAbbr;
    @JsonAlias({"company_name", "companyName"})
    private String companyName;
    @JsonAlias("manager_name")
    private String managerName;
    @JsonAlias("custodian_name")
    private String custodianName;
    @JsonAlias("establish_date")
    private LocalDate establishDate;
    private String benchmark;
    @JsonAlias("risk_level")
    private String riskLevel;
    @JsonAlias("fund_scale")
    private BigDecimal fundScale;
    private String status;
    private String source;
    @JsonAlias({"source_time", "sourceUpdatedAt"})
    private OffsetDateTime sourceTime;
    @JsonAlias("quality_status")
    private String qualityStatus;
    private String checksum;
    @JsonAlias("data_version")
    private String dataVersion;
}
