package org.dromara.fund.domain.bo;

import lombok.Data;

/**
 * 基金列表查询条件。
 */
@Data
public class FundQueryBo {

    private String fundCode;
    private String fundName;
    private String fundType;
    private String source;
    private String qualityStatus;
    private String syncStatus;
    private String status;
}
