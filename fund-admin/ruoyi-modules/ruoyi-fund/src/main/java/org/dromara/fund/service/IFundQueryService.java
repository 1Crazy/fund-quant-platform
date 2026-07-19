package org.dromara.fund.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.vo.FundDetailVo;
import org.dromara.fund.domain.vo.FundListVo;

/**
 * 基金查询服务。
 */
public interface IFundQueryService {

    TableDataInfo<FundListVo> queryPage(FundQueryBo bo, PageQuery pageQuery);

    FundDetailVo queryDetail(String fundCode, int days);
}
