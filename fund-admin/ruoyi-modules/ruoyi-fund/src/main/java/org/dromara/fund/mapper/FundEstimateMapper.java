package org.dromara.fund.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundEstimate;

import java.time.OffsetDateTime;

/**
 * 基金估值 Mapper。
 */
public interface FundEstimateMapper extends BaseMapperPlus<FundEstimate, FundEstimate> {

    FundEstimate selectLatestForRelease(
        @Param("fundCode") String fundCode,
        @Param("configReleaseVersion") Long configReleaseVersion,
        @Param("configReleaseChecksum") String configReleaseChecksum
    );

    /** 删除已超保留期且不是同基金同配置发布最新快照的记录。 */
    int deleteExpiredPreservingLatest(
        @Param("cutoff") OffsetDateTime cutoff,
        @Param("batchSize") int batchSize
    );
}
