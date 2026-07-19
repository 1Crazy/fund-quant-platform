package org.dromara.fund.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 基金数据同步运行记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fund_sync_run")
public class FundSyncRun extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键。 */
    @TableId
    private Long id;
    /** 数据集。 */
    private String dataset;
    /** 数据来源。 */
    private String source;
    /** 来源批次时间。 */
    private OffsetDateTime sourceTime;
    /** 业务日期。 */
    private LocalDate businessDate;
    /** 同步范围类型：ALL、FUND_CODE、DATE_RANGE、PARTITION。 */
    private String scopeType;
    /** 同步范围值。 */
    private String scopeValue;
    /** 分片键。 */
    private String partitionKey;
    /** 运行状态。 */
    private String state;
    /** 本批次发布质量状态。 */
    private String qualityStatus;
    /** 可续跑游标。 */
    private String cursorValue;
    /** 抓取批次 ID。 */
    private String fetchBatchId;
    /** 本次发布数据版本。 */
    private String dataVersion;
    /** 批次摘要校验和。 */
    private String checksum;
    /** 成功数量。 */
    private Integer successCount;
    /** 拒绝数量。 */
    private Integer rejectedCount;
    /** 失败数量。 */
    private Integer failedCount;
    /** 重试次数。 */
    private Integer retryCount;
    /** 上游调用累计耗时，毫秒。 */
    private Long upstreamLatencyMs;
    /** 过期数据数量。 */
    private Integer staleCount;
    /** 缓存失效数量。 */
    private Integer cacheInvalidatedCount;
    /** 开始时间。 */
    private OffsetDateTime startedAt;
    /** 结束时间。 */
    private OffsetDateTime finishedAt;
    /** 同步耗时，毫秒。 */
    private Long durationMs;
    /** 末次错误码。 */
    private String errorCode;
    /** 已脱敏错误摘要。 */
    private String errorMessage;
}
