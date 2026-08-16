package org.dromara.fund.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.dromara.fund.client.FundNavPositionProviderClient;
import org.dromara.fund.domain.FundNavPosition;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundNavPositionVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavPositionMapper;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 当前发布版本已有历史位置时必须直接读取持久化结果。 */
@Tag("dev")
final class FundNavPositionServiceImplTest {

    @Test
    void queryNavPositionReadsPersistedResultBeforeCallingProvider() {
        FundNavPositionProviderClient providerClient = mock(FundNavPositionProviderClient.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundNavPositionMapper positionMapper = mock(FundNavPositionMapper.class);
        FundNavPositionServiceImpl service = new FundNavPositionServiceImpl(
            providerClient,
            contextResolver,
            mock(FundInfoMapper.class),
            positionMapper,
            mock(ScheduledExecutorService.class),
            new ObjectMapper()
        );
        QuantConfigTaskContext context = new QuantConfigTaskContext();
        context.setConfigReleaseVersion(2L);
        context.setConfigReleaseChecksum("a".repeat(64));
        FundNavPosition persisted = new FundNavPosition();
        persisted.setFundCode("000001");
        persisted.setStatus("NORMAL");
        persisted.setNavPositionRegion("LOW_VALUATION");
        persisted.setNavPositionScore(new BigDecimal("24.5"));
        persisted.setTradeDate(LocalDate.of(2026, 8, 14));
        persisted.setCalculatedAt(OffsetDateTime.parse("2026-08-15T23:46:13+08:00"));
        persisted.setReasonsJson("[]");
        persisted.setIndicatorsJson("[]");
        when(contextResolver.pinActiveRelease()).thenReturn(context);
        when(positionMapper.selectForRelease("000001", 2L, "a".repeat(64))).thenReturn(persisted);

        FundNavPositionVo result = service.queryNavPosition("000001");

        assertEquals("LOW_VALUATION", result.getNavPositionRegion());
        assertEquals(new BigDecimal("24.5"), result.getNavPositionScore());
        assertEquals(LocalDate.of(2026, 8, 14), result.getTradeDate());
        verifyNoInteractions(providerClient);
    }
}
