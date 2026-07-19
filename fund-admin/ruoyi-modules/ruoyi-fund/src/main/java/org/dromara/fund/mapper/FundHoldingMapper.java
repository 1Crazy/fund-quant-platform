package org.dromara.fund.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundHolding;
import org.dromara.fund.domain.dto.FundDataVersionDto;
import org.dromara.fund.domain.vo.FundHoldingVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 基金披露持仓 Mapper。
 */
public interface FundHoldingMapper extends BaseMapperPlus<FundHolding, FundHolding> {

    int upsertBatch(@Param("items") List<FundHolding> items);

    List<FundHoldingVo> selectLatest(@Param("fundCode") String fundCode);

    List<FundHoldingVo> selectByReportDate(
        @Param("fundCode") String fundCode,
        @Param("reportDate") LocalDate reportDate
    );

    LocalDate selectLatestReportDate(@Param("fundCode") String fundCode);

    FundDataVersionDto selectLatestVersion(@Param("fundCode") String fundCode);
}
