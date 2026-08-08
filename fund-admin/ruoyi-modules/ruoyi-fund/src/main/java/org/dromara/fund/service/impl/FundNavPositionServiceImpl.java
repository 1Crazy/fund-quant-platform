package org.dromara.fund.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundNavPositionProviderClient;
import org.dromara.fund.config.FundEstimateRuntimeSettings;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.dto.NavPositionProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundNavPositionBatchStatusVo;
import org.dromara.fund.domain.vo.FundNavPositionVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.service.IFundNavPositionService;
import org.dromara.fund.service.QuantConfigTaskContextResolver;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** 由锁定的量化发布版本驱动的历史 NAV 位置服务。 */
@Service
@RequiredArgsConstructor
public class FundNavPositionServiceImpl implements IFundNavPositionService {

    private static final int BATCH_SIZE = 100;
    private static final Duration BATCH_STATUS_TTL = Duration.ofHours(24);

    private final FundNavPositionProviderClient providerClient;
    private final FundEstimateRuntimeSettings runtimeSettings;
    private final QuantConfigTaskContextResolver quantConfigTaskContextResolver;
    private final FundInfoMapper fundInfoMapper;
    private final ScheduledExecutorService scheduledExecutorService;
    private final Object batchSubmissionMonitor = new Object();

    @Override
    public FundNavPositionVo queryNavPosition(String fundCode) {
        QuantConfigTaskContext configContext = quantConfigTaskContextResolver.pinActiveRelease();
        return queryNavPosition(fundCode, configContext);
    }

    @Override
    public FundNavPositionBatchStatusVo submitBatchCalculation() {
        QuantConfigTaskContext configContext = quantConfigTaskContextResolver.pinActiveRelease();
        synchronized (batchSubmissionMonitor) {
            FundNavPositionBatchStatusVo existing = queryBatchCalculationStatus();
            RLock lock = RedisUtils.getClient().getLock(FundCacheConstants.NAV_POSITION_BATCH_LOCK);
            if ("RUNNING".equals(existing.getState()) && lock.isLocked()) {
                return existing;
            }

            FundNavPositionBatchStatusVo status = new FundNavPositionBatchStatusVo();
            status.setState("RUNNING");
            status.setConfigReleaseVersion(configContext.getConfigReleaseVersion());
            status.setRequestedCount(0);
            status.setProcessedCount(0);
            status.setNormalCount(0);
            status.setUnavailableCount(0);
            status.setFailedCount(0);
            status.setStartedAt(OffsetDateTime.now());
            storeBatchStatus(status);
            scheduledExecutorService.execute(() -> calculateAllNavPositions(configContext));
            return status;
        }
    }

    @Override
    public FundNavPositionBatchStatusVo queryBatchCalculationStatus() {
        FundNavPositionBatchStatusVo status = RedisUtils.getCacheObject(FundCacheConstants.NAV_POSITION_BATCH_STATUS_KEY);
        if (status != null) {
            return status;
        }
        FundNavPositionBatchStatusVo idle = new FundNavPositionBatchStatusVo();
        idle.setState("IDLE");
        idle.setRequestedCount(0);
        idle.setProcessedCount(0);
        idle.setNormalCount(0);
        idle.setUnavailableCount(0);
        idle.setFailedCount(0);
        return idle;
    }

    private void calculateAllNavPositions(QuantConfigTaskContext configContext) {
        RLock lock = RedisUtils.getClient().getLock(FundCacheConstants.NAV_POSITION_BATCH_LOCK);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            FundNavPositionBatchStatusVo status = queryBatchCalculationStatus();
            status.setRequestedCount(fundInfoMapper.countActiveFundCodesWithNav());
            storeBatchStatus(status);

            String cursor = null;
            int normal = 0;
            int unavailable = 0;
            int failed = 0;
            while (true) {
                List<String> fundCodes = fundInfoMapper.selectActiveFundCodesWithNavAfter(cursor, BATCH_SIZE);
                if (fundCodes.isEmpty()) {
                    break;
                }
                for (String fundCode : fundCodes) {
                    try {
                        FundNavPositionVo result = refreshNavPosition(fundCode, configContext);
                        if ("NORMAL".equals(result.getStatus()) && result.getNavPositionRegion() != null) {
                            normal++;
                        } else {
                            unavailable++;
                        }
                    } catch (RuntimeException error) {
                        failed++;
                        status.setErrorMessage(sanitize(error.getMessage()));
                    }
                    cursor = fundCode;
                    updateBatchProgress(status, cursor, normal, unavailable, failed);
                }
                if (fundCodes.size() < BATCH_SIZE) {
                    break;
                }
            }
            status.setState(failed == 0 ? "SUCCESS" : "PARTIAL_SUCCESS");
            status.setFinishedAt(OffsetDateTime.now());
            storeBatchStatus(status);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            markBatchFailed(error.getMessage());
        } catch (RuntimeException error) {
            markBatchFailed(error.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private FundNavPositionVo queryNavPosition(String fundCode, QuantConfigTaskContext configContext) {
        FundNavPositionVo cached = queryCached(fundCode, configContext);
        if (cached != null) {
            return cached;
        }
        NavPositionProviderResponse response = providerClient.fetch(fundCode, configContext);
        validate(response, fundCode, configContext);
        FundNavPositionVo result = fromProvider(response);
        RedisUtils.setCacheObject(cacheKey(fundCode, configContext), result, runtimeSettings.getCacheTtl());
        return result;
    }

    /** 批量计算必须基于当前确认净值重算，不能复用上一轮的热点缓存。 */
    private FundNavPositionVo refreshNavPosition(String fundCode, QuantConfigTaskContext configContext) {
        RedisUtils.deleteObject(cacheKey(fundCode, configContext));
        return queryNavPosition(fundCode, configContext);
    }

    private void updateBatchProgress(
        FundNavPositionBatchStatusVo status,
        String cursor,
        int normal,
        int unavailable,
        int failed
    ) {
        status.setCursorValue(cursor);
        status.setNormalCount(normal);
        status.setUnavailableCount(unavailable);
        status.setFailedCount(failed);
        status.setProcessedCount(normal + unavailable + failed);
        storeBatchStatus(status);
    }

    private void markBatchFailed(String errorMessage) {
        FundNavPositionBatchStatusVo status = queryBatchCalculationStatus();
        status.setState("FAILED");
        status.setFinishedAt(OffsetDateTime.now());
        status.setErrorMessage(sanitize(errorMessage));
        storeBatchStatus(status);
    }

    private void storeBatchStatus(FundNavPositionBatchStatusVo status) {
        RedisUtils.setCacheObject(FundCacheConstants.NAV_POSITION_BATCH_STATUS_KEY, status, BATCH_STATUS_TTL);
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "历史位置计算失败";
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    @Override
    public FundNavPositionVo queryCached(String fundCode, QuantConfigTaskContext configContext) {
        if (configContext == null || navPositionAlgorithmVersion(configContext) == null) {
            return null;
        }
        return RedisUtils.getCacheObject(cacheKey(fundCode, configContext));
    }

    private String cacheKey(String fundCode, QuantConfigTaskContext context) {
        return FundCacheConstants.navPositionCacheKey(
            fundCode,
            navPositionAlgorithmVersion(context),
            context.getConfigReleaseVersion(),
            context.getConfigReleaseChecksum()
        );
    }

    private String navPositionAlgorithmVersion(QuantConfigTaskContext context) {
        if (context == null || context.getGroups() == null) {
            return null;
        }
        QuantConfigReleaseReference.GroupReference group = context.getGroups().get("NAV_POSITION");
        return group == null || group.getSchemaVersion() == null ? null : "nav-position-v" + group.getSchemaVersion();
    }

    private void validate(
        NavPositionProviderResponse response,
        String fundCode,
        QuantConfigTaskContext configContext
    ) {
        if (!fundCode.equals(response.getFundCode())
            || !configContext.getConfigReleaseVersion().equals(response.getConfigReleaseVersion())
            || !configContext.getConfigReleaseChecksum().equals(response.getConfigReleaseChecksum())
            || !navPositionAlgorithmVersion(configContext).equals(response.getAlgorithmVersion())) {
            throw new ServiceException("QUANT_CONFIG_VERSION_MISMATCH");
        }
    }

    private FundNavPositionVo fromProvider(NavPositionProviderResponse response) {
        FundNavPositionVo result = new FundNavPositionVo();
        BeanUtils.copyProperties(response, result, "reasons", "indicators");
        result.setReasons(copyReasons(response.getReasons()));
        result.setIndicators(copyIndicators(response.getIndicators()));
        return result;
    }

    private List<FundNavPositionVo.NavPositionReasonVo> copyReasons(
        List<NavPositionProviderResponse.NavPositionReasonResponse> values
    ) {
        return (values == null ? List.<NavPositionProviderResponse.NavPositionReasonResponse>of() : values).stream()
            .map(value -> {
                FundNavPositionVo.NavPositionReasonVo target = new FundNavPositionVo.NavPositionReasonVo();
                BeanUtils.copyProperties(value, target);
                return target;
            })
            .toList();
    }

    private List<FundNavPositionVo.NavPositionIndicatorVo> copyIndicators(
        List<NavPositionProviderResponse.NavPositionIndicatorResponse> values
    ) {
        return (values == null ? List.<NavPositionProviderResponse.NavPositionIndicatorResponse>of() : values).stream()
            .map(value -> {
                FundNavPositionVo.NavPositionIndicatorVo target = new FundNavPositionVo.NavPositionIndicatorVo();
                BeanUtils.copyProperties(value, target);
                return target;
            })
            .toList();
    }
}
