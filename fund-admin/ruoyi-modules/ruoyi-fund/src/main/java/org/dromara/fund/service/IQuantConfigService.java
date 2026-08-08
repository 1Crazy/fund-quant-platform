package org.dromara.fund.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.domain.bo.QuantConfigDraftBo;
import org.dromara.fund.domain.bo.QuantConfigCloneBo;
import org.dromara.fund.domain.bo.QuantConfigReleaseBo;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.vo.QuantConfigDiffVo;
import org.dromara.fund.domain.vo.QuantConfigGroupVo;
import org.dromara.fund.domain.vo.QuantConfigReleaseVo;
import org.dromara.fund.domain.vo.QuantConfigVersionVo;

import java.util.List;

/** 量化配置草稿、校验、发布和版本血缘服务。 */
public interface IQuantConfigService {
    List<QuantConfigGroupVo> listGroups();

    TableDataInfo<QuantConfigVersionVo> queryVersionPage(String configCode, String status, PageQuery pageQuery);

    QuantConfigVersionVo queryVersion(Long id);

    QuantConfigVersionVo createDraft(QuantConfigDraftBo bo);

    QuantConfigVersionVo cloneDraft(Long sourceId, QuantConfigCloneBo bo);

    QuantConfigVersionVo updateDraft(Long id, QuantConfigDraftBo bo);

    QuantConfigVersionVo validateDraft(Long id, Long revision);

    QuantConfigDiffVo diff(Long baseId, Long targetId);

    List<QuantConfigReleaseVo> queryReleaseHistory();

    QuantConfigReleaseVo queryRelease(Long releaseVersion);

    QuantConfigReleaseVo publish(QuantConfigReleaseBo bo);

    QuantConfigReleaseVo rollback(Long releaseVersion, QuantConfigReleaseBo bo);

    QuantConfigReleaseReference resolveActiveRelease();

    /** 供显式历史重算加载指定发布版本；不按活动版本或最新版本替代。 */
    QuantConfigReleaseReference resolveRelease(Long releaseVersion);
}
