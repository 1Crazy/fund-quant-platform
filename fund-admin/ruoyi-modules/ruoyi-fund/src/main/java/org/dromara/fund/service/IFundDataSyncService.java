package org.dromara.fund.service;

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
}
