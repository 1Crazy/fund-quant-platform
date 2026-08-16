package org.dromara.fund.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundNavPosition;

/** 基金历史 NAV 位置结果 Mapper。 */
public interface FundNavPositionMapper extends BaseMapperPlus<FundNavPosition, FundNavPosition> {

    FundNavPosition selectForRelease(
        @Param("fundCode") String fundCode,
        @Param("configReleaseVersion") Long configReleaseVersion,
        @Param("configReleaseChecksum") String configReleaseChecksum
    );

    /** 同一基金、同一量化发布版本只保留本次计算的最新结果。 */
    int upsert(@Param("item") FundNavPosition position);
}
