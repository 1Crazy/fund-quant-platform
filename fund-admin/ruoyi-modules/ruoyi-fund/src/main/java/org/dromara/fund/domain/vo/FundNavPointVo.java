package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 基金净值序列点。
 */
@Data
public class FundNavPointVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private BigDecimal unitNav;
    private BigDecimal accumulatedNav;
    private BigDecimal dailyGrowthRate;
}
