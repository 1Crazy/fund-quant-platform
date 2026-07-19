package org.dromara.fund.domain.bo;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 基金同步运行查询条件。
 */
@Data
public class FundSyncRunQueryBo {

    private String fetchBatchId;
    private String dataset;
    private String scopeType;
    private String scopeValue;
    private String state;
    /** 前端兼容字段：同步类型。 */
    private String syncType;
    /** 前端兼容字段：运行状态。 */
    private String status;
    /** 前端兼容字段：基金代码。 */
    private String fundCode;
    /** 前端兼容字段：同步范围。 */
    private String syncScope;
    private OffsetDateTime startedAtStart;
    private OffsetDateTime startedAtEnd;
}
