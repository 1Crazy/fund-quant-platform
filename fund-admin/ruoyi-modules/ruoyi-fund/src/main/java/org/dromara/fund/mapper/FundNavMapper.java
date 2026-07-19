package org.dromara.fund.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundNav;
import org.dromara.fund.domain.vo.FundNavPointVo;

import java.util.List;

/**
 * 基金净值 Mapper。
 */
public interface FundNavMapper extends BaseMapperPlus<FundNav, FundNav> {

    FundNavPointVo selectLatest(@Param("fundCode") String fundCode);

    List<FundNavPointVo> selectSeries(@Param("fundCode") String fundCode, @Param("days") int days);
}
