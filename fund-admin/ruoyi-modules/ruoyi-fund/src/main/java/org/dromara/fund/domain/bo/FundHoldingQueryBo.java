package org.dromara.fund.domain.bo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 基金持仓查询条件。
 */
@Data
public class FundHoldingQueryBo {

    private String fundCode;
    private LocalDate reportDate;
    private String qualityStatus;
}
