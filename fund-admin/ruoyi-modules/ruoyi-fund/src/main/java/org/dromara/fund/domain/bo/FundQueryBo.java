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
    /** 当前发布版本下已计算的历史位置区域。 */
    private String navPositionRegion;
    private String status;
}
