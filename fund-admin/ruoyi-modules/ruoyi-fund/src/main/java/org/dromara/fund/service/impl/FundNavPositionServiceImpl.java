package org.dromara.fund.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundNavPositionProviderClient;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundNavPosition;
import org.dromara.fund.domain.dto.NavPositionProviderResponse;
import org.dromara.fund.domain.dto.QuantConfigReleaseReference;
import org.dromara.fund.domain.dto.QuantConfigTaskContext;
import org.dromara.fund.domain.vo.FundNavPositionBatchStatusVo;
import org.dromara.fund.domain.vo.FundNavPositionVo;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavPositionMapper;
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
    private final QuantConfigTaskContextResolver quantConfigTaskContextResolver;
    private final FundInfoMapper fundInfoMapper;
    private final FundNavPositionMapper fundNavPositionMapper;
    private final ScheduledExecutorService scheduledExecutorService;
    private final ObjectMapper objectMapper;
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
        FundNavPositionVo persisted = queryPersisted(fundCode, configContext);
        if (persisted != null) {
            return persisted;
        }
        return calculateAndPersist(fundCode, configContext);
    }

    /** 批量计算必须基于当前确认净值重算，并覆盖同一发布版本下的旧结果。 */
    private FundNavPositionVo refreshNavPosition(String fundCode, QuantConfigTaskContext configContext) {
        return calculateAndPersist(fundCode, configContext);
    }

    private FundNavPositionVo calculateAndPersist(String fundCode, QuantConfigTaskContext configContext) {
        NavPositionProviderResponse response = providerClient.fetch(fundCode, configContext);
        validate(response, fundCode, configContext);
        FundNavPositionVo result = fromProvider(response);
        persist(result);
        return result;
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

    private FundNavPositionVo queryPersisted(String fundCode, QuantConfigTaskContext configContext) {
        if (configContext == null || configContext.getConfigReleaseVersion() == null
            || configContext.getConfigReleaseChecksum() == null || configContext.getConfigReleaseChecksum().isBlank()) {
            return null;
        }
        return fromEntity(fundNavPositionMapper.selectForRelease(
            fundCode,
            configContext.getConfigReleaseVersion(),
            configContext.getConfigReleaseChecksum()
        ));
    }

    private void persist(FundNavPositionVo result) {
        FundNavPosition entity = new FundNavPosition();
        entity.setId(IdGeneratorUtil.nextLongId());
        entity.setFundCode(result.getFundCode());
        entity.setTradeDate(result.getTradeDate());
        entity.setCalculatedAt(result.getCalculatedAt());
        entity.setStatus(result.getStatus());
        entity.setAlgorithmVersion(result.getAlgorithmVersion());
        entity.setConfigReleaseVersion(result.getConfigReleaseVersion());
        entity.setConfigReleaseChecksum(result.getConfigReleaseChecksum());
        entity.setNavPositionConfigVersion(result.getNavPositionConfigVersion());
        entity.setNavPositionConfigChecksum(result.getNavPositionConfigChecksum());
        entity.setInputDataVersion(result.getInputDataVersion());
        entity.setNavPercentile(result.getNavPercentile());
        entity.setCurrentDrawdown(result.getCurrentDrawdown());
        entity.setMa60Deviation(result.getMa60Deviation());
        entity.setMa120Deviation(result.getMa120Deviation());
        entity.setMa250Deviation(result.getMa250Deviation());
        entity.setNavPositionScore(result.getNavPositionScore());
        entity.setNavPositionRegion(result.getNavPositionRegion());
        entity.setSampleCount(result.getSampleCount());
        entity.setEffectiveStartDate(result.getEffectiveStartDate());
        entity.setEffectiveEndDate(result.getEffectiveEndDate());
        entity.setReasonsJson(writeJson(result.getReasons()));
        entity.setIndicatorsJson(writeJson(result.getIndicators()));
        fundNavPositionMapper.upsert(entity);
    }

    private FundNavPositionVo fromEntity(FundNavPosition entity) {
        if (entity == null) {
            return null;
        }
        FundNavPositionVo result = new FundNavPositionVo();
        BeanUtils.copyProperties(entity, result);
        result.setReasons(readReasons(entity.getReasonsJson()));
        result.setIndicators(readIndicators(entity.getIndicatorsJson()));
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException error) {
            throw new ServiceException("历史位置结果序列化失败");
        }
    }

    private List<FundNavPositionVo.NavPositionReasonVo> readReasons(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FundNavPositionVo.NavPositionReasonVo>>() {
            });
        } catch (JsonProcessingException error) {
            throw new ServiceException("历史位置结果解释数据损坏");
        }
    }

    private List<FundNavPositionVo.NavPositionIndicatorVo> readIndicators(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<FundNavPositionVo.NavPositionIndicatorVo>>() {
            });
        } catch (JsonProcessingException error) {
            throw new ServiceException("历史位置结果指标数据损坏");
        }
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
