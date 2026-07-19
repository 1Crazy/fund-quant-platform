package org.dromara.fund.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.domain.bo.FundDataQualityIssueQueryBo;
import org.dromara.fund.domain.bo.FundSyncRunQueryBo;
import org.dromara.fund.domain.dto.FundSyncStatusSummaryVo;
import org.dromara.fund.domain.vo.FundDataQualityIssueVo;
import org.dromara.fund.domain.vo.FundHoldingVo;
import org.dromara.fund.domain.vo.FundSyncRunVo;

import java.time.LocalDate;
import java.util.List;

/**
 * 基金基础数据同步服务。
 */
public interface IFundDataSyncService {

    /**
     * 本地不存在基金主数据或历史净值时，从 fund-quant 补齐数据。
     *
     * @param fundCode 六位基金代码
     * @param days 所需净值日数量，0 表示全部历史
     * @return true 表示本次执行了同步，false 表示本地数据已完整
     */
    boolean ensureAvailable(String fundCode, int days);

    /**
     * 将名称或拼音缩写匹配的基金目录同步到本地，供 PostgreSQL 完成标准分页。
     *
     * @param keyword 基金名称或拼音缩写
     * @return 本次上游匹配数量
     */
    int syncCatalogMatches(String keyword);

    /**
     * 执行全量目录初始化的一个分页分区。
     *
     * @param cursorValue 页码游标，空表示第一页
     * @return 同步运行记录
     */
    FundSyncRunVo runFullInitPartition(String cursorValue);

    /**
     * 执行日常增量同步入口。
     *
     * @return 同步运行记录
     */
    FundSyncRunVo runIncremental();

    /**
     * 手动触发单基金档案、净值和持仓同步。
     */
    FundSyncRunVo triggerFundSync(String fundCode, int days);

    /**
     * 按指定净值区间触发单基金同步。
     */
    FundSyncRunVo triggerFundSync(String fundCode, LocalDate startDate, LocalDate endDate);

    /**
     * 最新持仓。
     */
    List<FundHoldingVo> queryLatestHoldings(String fundCode, LocalDate reportDate);

    TableDataInfo<FundSyncRunVo> queryRunPage(FundSyncRunQueryBo bo, PageQuery pageQuery);

    FundSyncRunVo queryRunDetail(Long id);

    /**
     * 按原运行范围重试失败或部分成功的同步任务。
     */
    FundSyncRunVo retryRun(Long id);

    FundSyncStatusSummaryVo queryStatus(String dataset, String scopeType, String scopeValue);

    TableDataInfo<FundDataQualityIssueVo> queryIssuePage(FundDataQualityIssueQueryBo bo, PageQuery pageQuery);
}
