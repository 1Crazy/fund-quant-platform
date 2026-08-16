package org.dromara.fund.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.fund.client.FundDataProviderClient;
import org.dromara.fund.config.FundDataProperties;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.domain.bo.FundQueryBo;
import org.dromara.fund.domain.vo.FundListVo;
import org.dromara.fund.mapper.FundDataQualityIssueMapper;
import org.dromara.fund.mapper.FundHoldingMapper;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavMapper;
import org.dromara.fund.service.IFundDataSyncService;
import org.dromara.fund.service.IFundEstimateService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 基金分页必须使用轻量计数，避免分页插件为 count 执行展示关联。 */
@Tag("dev")
final class FundQueryServiceImplTest {

    @Test
    void queryPageUsesDedicatedCountWithoutAutomaticPaginationCount() {
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundQueryServiceImpl service = new FundQueryServiceImpl(
            fundInfoMapper,
            mock(FundNavMapper.class),
            mock(FundHoldingMapper.class),
            mock(FundDataQualityIssueMapper.class),
            mock(FundDataProviderClient.class),
            mock(FundDataProperties.class),
            runtimeSettings,
            mock(IFundDataSyncService.class),
            mock(IFundEstimateService.class),
            contextResolver
        );
        FundQueryBo query = new FundQueryBo();
        when(contextResolver.pinActiveRelease()).thenThrow(new ServiceException("QUANT_CONFIG_NOT_PUBLISHED"));
        when(runtimeSettings.getStaleAfter()).thenReturn(Duration.ofSeconds(300));
        when(fundInfoMapper.countFundPage(query, null, null)).thenReturn(37L);
        when(fundInfoMapper.selectFundPage(any(), same(query), isNull(), isNull(), anyLong()))
            .thenAnswer(invocation -> {
                Page<FundListVo> page = invocation.getArgument(0);
                page.setRecords(List.of());
                return page;
            });

        TableDataInfo<FundListVo> result = service.queryPage(query, new PageQuery(20, 1));

        assertEquals(37L, result.getTotal());
        verify(fundInfoMapper).countFundPage(query, null, null);
        verify(fundInfoMapper).selectFundPage(
            argThat(page -> !page.searchCount()),
            same(query),
            isNull(),
            isNull(),
            eq(300L)
        );
    }

    @Test
    void queryPageReturnsLocalNameMatchesWithoutCallingCatalogProvider() {
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        IFundDataSyncService dataSyncService = mock(IFundDataSyncService.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundQueryServiceImpl service = new FundQueryServiceImpl(
            fundInfoMapper,
            mock(FundNavMapper.class),
            mock(FundHoldingMapper.class),
            mock(FundDataQualityIssueMapper.class),
            mock(FundDataProviderClient.class),
            mock(FundDataProperties.class),
            runtimeSettings,
            dataSyncService,
            mock(IFundEstimateService.class),
            contextResolver
        );
        FundQueryBo query = new FundQueryBo();
        query.setFundName(" 中海可转债债券A ");
        when(contextResolver.pinActiveRelease()).thenThrow(new ServiceException("QUANT_CONFIG_NOT_PUBLISHED"));
        when(runtimeSettings.getStaleAfter()).thenReturn(Duration.ofSeconds(300));
        when(fundInfoMapper.countFundPage(query, null, null)).thenReturn(1L);
        when(fundInfoMapper.selectFundPage(any(), same(query), isNull(), isNull(), anyLong()))
            .thenAnswer(invocation -> {
                Page<FundListVo> page = invocation.getArgument(0);
                page.setRecords(List.of());
                return page;
            });

        TableDataInfo<FundListVo> result = service.queryPage(query, new PageQuery(20, 1));

        assertEquals(1L, result.getTotal());
        assertEquals("中海可转债债券A", query.getFundName());
        verify(dataSyncService, never()).syncCatalogMatches(any());
    }

    @Test
    void queryPageDoesNotCallCatalogProviderWhenLocalNameHasNoMatch() {
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        IFundDataSyncService dataSyncService = mock(IFundDataSyncService.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundQueryServiceImpl service = new FundQueryServiceImpl(
            fundInfoMapper,
            mock(FundNavMapper.class),
            mock(FundHoldingMapper.class),
            mock(FundDataQualityIssueMapper.class),
            mock(FundDataProviderClient.class),
            mock(FundDataProperties.class),
            runtimeSettings,
            dataSyncService,
            mock(IFundEstimateService.class),
            contextResolver
        );
        FundQueryBo query = new FundQueryBo();
        query.setFundName("华夏");
        when(contextResolver.pinActiveRelease()).thenThrow(new ServiceException("QUANT_CONFIG_NOT_PUBLISHED"));
        when(runtimeSettings.getStaleAfter()).thenReturn(Duration.ofSeconds(300));
        when(fundInfoMapper.countFundPage(query, null, null)).thenReturn(0L);
        when(fundInfoMapper.selectFundPage(any(), same(query), isNull(), isNull(), anyLong()))
            .thenAnswer(invocation -> {
                Page<FundListVo> page = invocation.getArgument(0);
                page.setRecords(List.of());
                return page;
            });

        TableDataInfo<FundListVo> result = service.queryPage(query, new PageQuery(20, 1));

        assertEquals(0L, result.getTotal());
        verify(dataSyncService, never()).syncCatalogMatches(any());
        verify(fundInfoMapper).selectFundPage(any(), same(query), isNull(), isNull(), eq(300L));
    }

    @Test
    void queryPageDoesNotSyncAnExactFundCode() {
        FundInfoMapper fundInfoMapper = mock(FundInfoMapper.class);
        FundEstimateRuntimeSettings runtimeSettings = mock(FundEstimateRuntimeSettings.class);
        IFundDataSyncService dataSyncService = mock(IFundDataSyncService.class);
        QuantConfigTaskContextResolver contextResolver = mock(QuantConfigTaskContextResolver.class);
        FundQueryServiceImpl service = new FundQueryServiceImpl(
            fundInfoMapper,
            mock(FundNavMapper.class),
            mock(FundHoldingMapper.class),
            mock(FundDataQualityIssueMapper.class),
            mock(FundDataProviderClient.class),
            mock(FundDataProperties.class),
            runtimeSettings,
            dataSyncService,
            mock(IFundEstimateService.class),
            contextResolver
        );
        FundQueryBo query = new FundQueryBo();
        query.setFundCode("000003");
        when(contextResolver.pinActiveRelease()).thenThrow(new ServiceException("QUANT_CONFIG_NOT_PUBLISHED"));
        when(runtimeSettings.getStaleAfter()).thenReturn(Duration.ofSeconds(300));
        when(fundInfoMapper.countFundPage(query, null, null)).thenReturn(1L);
        when(fundInfoMapper.selectFundPage(any(), same(query), isNull(), isNull(), anyLong()))
            .thenAnswer(invocation -> {
                Page<FundListVo> page = invocation.getArgument(0);
                page.setRecords(List.of());
                return page;
            });

        TableDataInfo<FundListVo> result = service.queryPage(query, new PageQuery(20, 1));

        assertEquals(1L, result.getTotal());
        verify(dataSyncService, never()).ensureAvailable(any(), anyInt());
    }
}
