package org.dromara.fund.service;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 供后续估值、风险、组合和回测任务在创建时固定精确发布版本。 */
@Component
@RequiredArgsConstructor
public class QuantConfigTaskContextResolver {
    private final IQuantConfigService configService;

    public QuantConfigTaskContext pinActiveRelease() {
        return toTaskContext(configService.resolveActiveRelease());
    }

    /** 历史重算只能使用调用方明确提供并校验过校验和的发布版本。 */
    public QuantConfigTaskContext pinRelease(Long releaseVersion, String releaseChecksum) {
        QuantConfigReleaseReference release = configService.resolveRelease(releaseVersion);
        if (!Objects.equals(release.getReleaseChecksum(), releaseChecksum)) {
            throw new ServiceException("QUANT_CONFIG_CHECKSUM_MISMATCH");
        }
        return toTaskContext(release);
    }

    private QuantConfigTaskContext toTaskContext(QuantConfigReleaseReference release) {
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(release.getReleaseVersion());
        context.setConfigReleaseChecksum(release.getReleaseChecksum());
        context.setGroups(release.getGroups());
        return context;
    }

    public void assertMatches(QuantConfigTaskContext expected, Long releaseVersion, String releaseChecksum) {
        if (!Objects.equals(expected.getConfigReleaseVersion(), releaseVersion)
            || !Objects.equals(expected.getConfigReleaseChecksum(), releaseChecksum)) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
    }
}
