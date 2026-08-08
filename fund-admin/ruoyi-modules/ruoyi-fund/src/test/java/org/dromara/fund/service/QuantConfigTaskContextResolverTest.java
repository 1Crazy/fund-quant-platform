package org.dromara.fund.service;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 任务创建时必须复制配置血缘，后续发布和调用方对象变更不能影响已固定任务。 */
@Tag("dev")
final class QuantConfigTaskContextResolverTest {

    @Test
    void pinningActiveReleaseCreatesAnImmutableLineageSnapshot() {
        IQuantConfigService configService = mock(IQuantConfigService.class);
        Map<String, QuantConfigReleaseReference.GroupReference> sourceGroups = new LinkedHashMap<>();
        QuantConfigReleaseReference.GroupReference estimate = group(2, 2, "a".repeat(64));
        sourceGroups.put("ESTIMATE", estimate);
        QuantConfigReleaseReference release = new QuantConfigReleaseReference();
        release.setReleaseVersion(12L);
        release.setReleaseChecksum("b".repeat(64));
        release.setGroups(sourceGroups);
        when(configService.resolveActiveRelease()).thenReturn(release);

        QuantConfigTaskContext context = new QuantConfigTaskContextResolver(configService).pinActiveRelease();

        sourceGroups.clear();
        estimate.setConfigVersion(99);
        estimate.setChecksum("c".repeat(64));
        context.getGroups().get("ESTIMATE").setConfigVersion(88);

        assertEquals(12L, context.getConfigReleaseVersion());
        assertEquals("b".repeat(64), context.getConfigReleaseChecksum());
        assertEquals(2, context.getGroups().get("ESTIMATE").getConfigVersion());
        assertEquals("a".repeat(64), context.getGroups().get("ESTIMATE").getChecksum());
        assertThrows(UnsupportedOperationException.class,
            () -> context.getGroups().put("TREND", group(1, 1, "d".repeat(64))));
    }

    @Test
    void resultMetadataMustExactlyMatchThePinnedRelease() {
        QuantConfigTaskContextResolver resolver = new QuantConfigTaskContextResolver(mock(IQuantConfigService.class));
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(12L);
        context.setConfigReleaseChecksum("b".repeat(64));

        resolver.assertMatches(context, 12L, "b".repeat(64));
        ServiceException error = assertThrows(ServiceException.class,
            () -> resolver.assertMatches(context, 13L, "b".repeat(64)));

        assertTrue(error.getMessage().startsWith("QUANT_CONFIG_"));
    }

    @Test
    void historicalTaskMustUseTheRequestedReleaseAndChecksum() {
        IQuantConfigService configService = mock(IQuantConfigService.class);
        QuantConfigReleaseReference release = new QuantConfigReleaseReference();
        release.setReleaseVersion(4L);
        release.setReleaseChecksum("a".repeat(64));
        release.setGroups(Map.of("ESTIMATE", group(1, 1, "b".repeat(64))));
        when(configService.resolveRelease(4L)).thenReturn(release);
        QuantConfigTaskContextResolver resolver = new QuantConfigTaskContextResolver(configService);

        QuantConfigTaskContext context = resolver.pinRelease(4L, "a".repeat(64));

        assertEquals(4L, context.getConfigReleaseVersion());
        assertEquals("a".repeat(64), context.getConfigReleaseChecksum());
        ServiceException error = assertThrows(ServiceException.class,
            () -> resolver.pinRelease(4L, "c".repeat(64)));
        assertEquals("QUANT_CONFIG_CHECKSUM_MISMATCH", error.getMessage());
    }

    private QuantConfigReleaseReference.GroupReference group(
        int configVersion,
        int schemaVersion,
        String checksum
    ) {
        QuantConfigReleaseReference.GroupReference group = new QuantConfigReleaseReference.GroupReference();
        group.setConfigVersion(configVersion);
        group.setSchemaVersion(schemaVersion);
        group.setChecksum(checksum);
        return group;
    }
}
