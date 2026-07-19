package org.dromara.fund.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundEstimate;

/**
 * 基金估值 Mapper。
 */
public interface FundEstimateMapper extends BaseMapperPlus<FundEstimate, FundEstimate> {

    FundEstimate selectLatest(@Param("fundCode") String fundCode);
}
