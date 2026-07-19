package org.dromara.fund.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.vo.FundListVo;

/**
 * 基金基础信息 Mapper。
 */
public interface FundInfoMapper extends BaseMapperPlus<FundInfo, FundInfo> {

    Page<FundListVo> selectFundPage(@Param("page") Page<FundListVo> page, @Param("bo") FundQueryBo bo);

    /** PostgreSQL 按基金代码幂等写入基础信息。 */
    int upsert(@Param("item") FundInfo fundInfo);
}
