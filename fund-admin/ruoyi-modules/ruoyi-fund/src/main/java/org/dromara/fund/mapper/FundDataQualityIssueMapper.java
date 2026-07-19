package org.dromara.fund.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundDataQualityIssue;
import org.dromara.fund.domain.bo.FundDataQualityIssueQueryBo;
import org.dromara.fund.domain.vo.FundDataQualityIssueVo;

import java.util.List;

/**
 * 基金数据质量问题 Mapper。
 */
public interface FundDataQualityIssueMapper extends BaseMapperPlus<FundDataQualityIssue, FundDataQualityIssue> {

    int upsert(@Param("item") FundDataQualityIssue item);

    Page<FundDataQualityIssueVo> selectIssuePage(
        @Param("page") Page<FundDataQualityIssueVo> page,
        @Param("bo") FundDataQualityIssueQueryBo bo
    );

    List<FundDataQualityIssueVo> selectRecentByFundCode(@Param("fundCode") String fundCode);
}
