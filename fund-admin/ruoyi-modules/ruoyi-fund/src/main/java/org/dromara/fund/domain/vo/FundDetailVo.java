package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 基金详情视图。
 */
@Data
public class FundDetailVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fundCode;
    private String fundName;
    private String fundType;
    private String managerName;
    private LocalDate establishDate;
    private String benchmark;
    private String riskLevel;
    private BigDecimal fundScale;
    private BigDecimal latestNav;
    private LocalDate navDate;
    private FundEstimateVo estimate;
    private List<FundNavPointVo> navSeries = List.of();
}
