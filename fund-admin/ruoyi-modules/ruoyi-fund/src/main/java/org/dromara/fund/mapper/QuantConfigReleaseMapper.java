package org.dromara.fund.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.dromara.fund.domain.QuantConfigRelease;
import org.dromara.fund.domain.QuantConfigReleaseItem;
import org.dromara.fund.domain.vo.QuantConfigReleaseVo;
import org.dromara.fund.domain.vo.QuantConfigVersionVo;

import java.util.List;

/** 发布清单与其不可变条目持久化接口。 */
public interface QuantConfigReleaseMapper extends BaseMapper<QuantConfigRelease> {
    long selectNextReleaseVersion();

    List<QuantConfigReleaseVo> selectReleaseHistory();

    QuantConfigReleaseVo selectReleaseVo(@Param("releaseVersion") Long releaseVersion);

    List<QuantConfigVersionVo> selectReleaseItems(@Param("releaseVersion") Long releaseVersion);

    QuantConfigRelease selectActiveRelease();

    int insertItem(@Param("item") QuantConfigReleaseItem item);
}
