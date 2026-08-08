package org.dromara.fund.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/** 全量历史位置计算的后台运行摘要。 */
@Data
public class FundNavPositionBatchStatusVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** IDLE、RUNNING、SUCCESS、PARTIAL_SUCCESS 或 FAILED。 */
    private String state;
    /** 本次锁定的量化配置发布版本。 */
    private Long configReleaseVersion;
    /** 待计算的、至少拥有一条确认净值的基金数量。 */
    private Integer requestedCount;
    /** 已完成处理的基金数量。 */
    private Integer processedCount;
    /** 已得到低位、正常、高位或风险区域的基金数量。 */
    private Integer normalCount;
    /** 净值样本不足等不可形成区域的基金数量。 */
    private Integer unavailableCount;
    /** 上游请求或结果校验失败的基金数量。 */
    private Integer failedCount;
    /** 已成功处理到的基金代码游标。 */
    private String cursorValue;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String errorMessage;
}
