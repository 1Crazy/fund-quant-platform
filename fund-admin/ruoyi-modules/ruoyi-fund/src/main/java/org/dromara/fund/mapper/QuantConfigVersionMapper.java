package org.dromara.fund.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.fund.domain.QuantConfigVersion;
import org.dromara.fund.domain.vo.QuantConfigVersionVo;

import java.util.List;

/** 量化配置版本持久化接口。 */
public interface QuantConfigVersionMapper extends BaseMapperPlus<QuantConfigVersion, QuantConfigVersion> {
    Page<QuantConfigVersionVo> selectVersionPage(@Param("page") Page<QuantConfigVersionVo> page,
                                                  @Param("configCode") String configCode,
                                                  @Param("status") String status);

    QuantConfigVersionVo selectVersionVo(@Param("id") Long id);

    Integer selectNextVersion(@Param("configCode") String configCode);

    int updateDraft(@Param("item") QuantConfigVersion item, @Param("expectedRevision") Long expectedRevision);

    int markValidated(@Param("id") Long id, @Param("expectedRevision") Long expectedRevision);

    List<QuantConfigVersion> selectByIdsForRelease(@Param("ids") List<Long> ids, @Param("forUpdate") boolean forUpdate);
}
