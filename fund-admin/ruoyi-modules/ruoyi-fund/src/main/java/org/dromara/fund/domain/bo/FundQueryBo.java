package org.dromara.fund.domain.bo;

import lombok.Data;

import java.util.List;

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
    /** 服务端从 Redis 投影出的匹配基金代码，不由请求参数传入。 */
    private List<String> navPositionFundCodes;
    private String status;
}
