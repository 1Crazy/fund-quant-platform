package org.dromara.fund.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * fund-quant 基金基础信息响应。
 */
@Data
public class FundProviderResponse {

    @JsonProperty("fund_code")
    private String fundCode;
    @JsonProperty("fund_name")
    private String fundName;
    @JsonProperty("fund_type")
    private String fundType;
    @JsonProperty("pinyin_abbr")
    private String pinyinAbbr;
    @JsonProperty("manager_name")
    private String managerName;
    @JsonProperty("custodian_name")
    private String custodianName;
    @JsonProperty("establish_date")
    private LocalDate establishDate;
    private String benchmark;
    @JsonProperty("risk_level")
    private String riskLevel;
    @JsonProperty("fund_scale")
    private BigDecimal fundScale;
    private String source;
}
