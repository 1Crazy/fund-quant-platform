package org.dromara.fund.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基金列表视图。
 */
@Data
public class FundListVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String fundCode;
    private String fundName;
    private String fundType;
    private BigDecimal latestNav;
    private LocalDate navDate;
    private BigDecimal estimateNav;
    private BigDecimal estimateGrowthRate;
    private LocalDateTime estimateTime;
    @JsonProperty("isStale")
    private boolean stale;
}
