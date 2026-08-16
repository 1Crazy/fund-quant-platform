package org.dromara.fund.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.dto.FundDataVersionDto;
import org.dromara.fund.domain.vo.FundListVo;

import java.util.List;

/**
 * 基金基础信息 Mapper。
 */
public interface FundInfoMapper extends BaseMapperPlus<FundInfo, FundInfo> {

    /**
     * 估值查询只读检查：确认基金、最新净值和最新披露持仓均已通过数据质量校验。
     */
    boolean hasReadyEstimateInputs(@Param("fundCode") String fundCode);

    /** 只选择数据中心输入完整的热点基金，定时估值不得全表扫基金或触发同步。 */
    List<String> selectReadyEstimateFundCodes(
        @Param("fundCodes") List<String> fundCodes,
        @Param("batchSize") int batchSize
    );

    /**
     * 供 SnailJob 历史重算按稳定哈希分片扫描合格基金；游标仅在当前分片内续跑。
     */
    List<String> selectReadyEstimateFundCodesForShard(
        @Param("lastFundCode") String lastFundCode,
        @Param("shardIndex") int shardIndex,
        @Param("shardTotal") int shardTotal,
        @Param("batchSize") int batchSize
    );

    /**
     * 按基金代码游标扫描当前有效基金，供长时全局同步逐批续跑。
     */
    List<String> selectActiveFundCodesAfter(
        @Param("lastFundCode") String lastFundCode,
        @Param("batchSize") int batchSize
    );

    /** 当前可参与全局同步的基金目录总数。 */
    int countActiveFundCodes();

    /** 根据全局同步游标计算已扫描的目录数量。 */
    int countActiveFundCodesThrough(@Param("lastFundCode") String lastFundCode);

    /** 选择至少拥有一条确认净值的有效基金，供历史位置全量计算分批扫描。 */
    List<String> selectActiveFundCodesWithNavAfter(
        @Param("lastFundCode") String lastFundCode,
        @Param("batchSize") int batchSize
    );

    /** 统计可参与历史位置计算的有效基金数量。 */
    int countActiveFundCodesWithNav();

    /**
     * 基金列表轻量计数，不执行列表展示所需的 NAV、持仓、同步和估值关联。
     */
    long countFundPage(
        @Param("bo") FundQueryBo bo,
        @Param("configReleaseVersion") Long configReleaseVersion,
        @Param("configReleaseChecksum") String configReleaseChecksum
    );

    Page<FundListVo> selectFundPage(
        @Param("page") Page<FundListVo> page,
        @Param("bo") FundQueryBo bo,
        @Param("configReleaseVersion") Long configReleaseVersion,
        @Param("configReleaseChecksum") String configReleaseChecksum,
        @Param("estimateStaleAfterSeconds") long estimateStaleAfterSeconds
    );

    /** PostgreSQL 按基金代码幂等写入基础信息。 */
    int upsert(@Param("item") FundInfo fundInfo);

    FundDataVersionDto selectLatestVersion(@Param("fundCode") String fundCode);
}
