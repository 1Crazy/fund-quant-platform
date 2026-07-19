package org.dromara.fund.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundNav;
import org.dromara.fund.domain.vo.FundNavPointVo;

import java.util.List;
import java.time.LocalDate;

/**
 * 基金净值 Mapper。
 */
public interface FundNavMapper extends BaseMapperPlus<FundNav, FundNav> {

    FundNavPointVo selectLatest(@Param("fundCode") String fundCode);

    List<FundNavPointVo> selectSeries(
        @Param("fundCode") String fundCode,
        @Param("startDate") LocalDate startDate
    );

    /** 查询已完成的最大上游同步周期，全部历史返回 5000。 */
    int selectSyncCoverage(@Param("fundCode") String fundCode);

    /** PostgreSQL 按基金代码和净值日期幂等写入。 */
    int upsertBatch(@Param("items") List<FundNav> items);
}
