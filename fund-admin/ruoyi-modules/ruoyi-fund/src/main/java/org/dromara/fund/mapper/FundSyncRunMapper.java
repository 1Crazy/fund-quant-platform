package org.dromara.fund.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundSyncRun;
import org.dromara.fund.domain.bo.FundSyncRunQueryBo;
import org.dromara.fund.domain.dto.FundSyncStatusSummaryVo;
import org.dromara.fund.domain.vo.FundSyncRunVo;

import java.util.List;

/**
 * 基金同步运行 Mapper。
 */
public interface FundSyncRunMapper extends BaseMapperPlus<FundSyncRun, FundSyncRun> {

    Page<FundSyncRunVo> selectRunPage(@Param("page") Page<FundSyncRunVo> page, @Param("bo") FundSyncRunQueryBo bo);

    FundSyncRunVo selectRunDetail(@Param("id") Long id);

    List<FundSyncRunVo> selectRunning(@Param("dataset") String dataset, @Param("scopeType") String scopeType, @Param("scopeValue") String scopeValue);

    /** 最近一次全局确认净值同步。 */
    FundSyncRunVo selectLatestGlobalNavRun();

    FundSyncStatusSummaryVo selectLatestStatus(@Param("dataset") String dataset, @Param("scopeType") String scopeType, @Param("scopeValue") String scopeValue);
}
