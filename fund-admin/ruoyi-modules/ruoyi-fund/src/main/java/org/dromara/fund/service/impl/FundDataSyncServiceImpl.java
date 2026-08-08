package org.dromara.fund.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.utils.IdGeneratorUtil;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.fund.client.FundDataProviderClient;
import org.dromara.fund.client.FundProviderException;
import org.dromara.fund.config.FundDataProperties;
import org.dromara.fund.constant.FundCacheConstants;
import org.dromara.fund.domain.FundDataQualityIssue;
import org.dromara.fund.domain.FundHolding;
import org.dromara.fund.domain.FundInfo;
import org.dromara.fund.domain.FundNav;
import org.dromara.fund.domain.FundSyncRun;
import org.dromara.fund.domain.bo.FundDataQualityIssueQueryBo;
import org.dromara.fund.domain.bo.FundSyncRunQueryBo;
import org.dromara.fund.domain.dto.FundHoldingProviderResponse;
import org.dromara.fund.domain.dto.FundNavProviderResponse;
import org.dromara.fund.domain.dto.FundProviderQualityIssueDto;
import org.dromara.fund.domain.dto.FundProviderResponse;
import org.dromara.fund.domain.dto.FundSyncBatchMetaDto;
import org.dromara.fund.domain.dto.FundSyncEnvelope;
import org.dromara.fund.domain.dto.FundSyncStatusSummaryVo;
import org.dromara.fund.domain.enums.FundDataQualityStatusEnum;
import org.dromara.fund.domain.enums.FundDatasetEnum;
import org.dromara.fund.domain.enums.FundQualityIssueStatusEnum;
import org.dromara.fund.domain.enums.FundQualityReasonCodeEnum;
import org.dromara.fund.domain.enums.FundSyncStatusEnum;
import org.dromara.fund.domain.vo.FundNavPointVo;
import org.dromara.fund.domain.vo.FundGlobalNavSyncStatusVo;
import org.dromara.fund.domain.vo.FundSyncRunVo;
import org.dromara.fund.mapper.FundDataQualityIssueMapper;
import org.dromara.fund.mapper.FundHoldingMapper;
import org.dromara.fund.mapper.FundInfoMapper;
import org.dromara.fund.mapper.FundNavMapper;
import org.dromara.fund.mapper.FundSyncRunMapper;
import org.dromara.fund.service.IFundDataSyncService;
import org.redisson.api.RLock;
import org.redisson.api.RateType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 基金数据中心同步实现。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FundDataSyncServiceImpl implements IFundDataSyncService {

    private static final long LOCK_WAIT_SECONDS = 3L;
    private static final int PROVIDER_RATE_INTERVAL_SECONDS = 60;
    /** 全局净值任务只请求确认净值，按每分钟额度平滑调度。 */
    private static final int GLOBAL_NAV_REQUESTS_PER_FUND = 1;
    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String FULL_HISTORY_SCOPE = "FULL_HISTORY";
    private static final String CONTINUE_FROM_LATEST_NAV_SCOPE = "CONTINUE_FROM_LATEST_NAV";
    private static final String GLOBAL_NAV_CATALOG_DONE = "CATALOG_DONE";
    private static final String GLOBAL_NAV_CATALOG_PAGE_PREFIX = "CATALOG_PAGE:";

    private final FundInfoMapper fundInfoMapper;
    private final FundNavMapper fundNavMapper;
    private final FundHoldingMapper fundHoldingMapper;
    private final FundSyncRunMapper fundSyncRunMapper;
    private final FundDataQualityIssueMapper qualityIssueMapper;
    private final FundDataProviderClient providerClient;
    private final FundDataProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final ScheduledExecutorService scheduledExecutorService;

    @Override
    public boolean ensureAvailable(String fundCode, int days) {
        int requestedDays = normalizeDays(days);
        String normalizedCode = normalizeFundCode(fundCode);
        if (hasCompleteLocalData(normalizedCode, requestedDays)) {
            return false;
        }
        ensureSyncEnabled();
        FundSyncRunVo run = triggerFundSync(normalizedCode, requestedDays);
        return FundSyncStatusEnum.SUCCESS.getCode().equals(run.getState())
            || FundSyncStatusEnum.PARTIAL_SUCCESS.getCode().equals(run.getState());
    }

    @Override
    public int syncCatalogMatches(String keyword) {
        if (!properties.isEnabled()) {
            return 0;
        }
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        String batchId = newBatchId("catalog_search");
        FundSyncRun run = createRun(FundDatasetEnum.FUND_CATALOG.getCode(), "SEARCH", normalized, null, batchId);
        try {
            List<FundProviderResponse> matches = withRateLimitAndRetry(run,
                () -> providerClient.searchFunds(normalized, properties.getSearchLimit()));
            PersistResult result = persistCatalog(run, matches, null);
            finishRun(run, result.state(), null, null, null);
            return matches.size();
        } catch (Exception e) {
            finishRun(run, FundSyncStatusEnum.FAILED.getCode(), null, errorCode(e), sanitize(e.getMessage()));
            throw toServiceException("基金目录同步失败", e);
        }
    }

    @Override
    public FundSyncRunVo runFullInitPartition(String cursorValue) {
        ensureSyncEnabled();
        String batchId = newBatchId("full_init");
        int page = parsePage(cursorValue);
        FundSyncRun run = createRun(FundDatasetEnum.FUND_CATALOG.getCode(), "ALL", null, String.valueOf(page), batchId);
        RLock lock = RedisUtils.getClient().getLock(FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "full:init");
        return withLock(lock, () -> {
            try {
                FundSyncEnvelope<FundProviderResponse> envelope = withRateLimitAndRetry(run,
                    () -> providerClient.syncCatalog(batchId, page, properties.getCatalogPageSize()));
                applyMeta(run, envelope.getMeta());
                PersistResult result = persistCatalog(run, envelope.getRecords(), envelope);
                FundSyncBatchMetaDto meta = envelope.getMeta();
                String nextCursor = meta != null && Boolean.TRUE.equals(meta.getHasMore()) && meta.getNextPage() != null
                    ? String.valueOf(meta.getNextPage()) : null;
                finishRun(run, result.state(), nextCursor, null, null);
                return requireRunVo(run.getId());
            } catch (Exception e) {
                finishRun(run, FundSyncStatusEnum.FAILED.getCode(), run.getCursorValue(), errorCode(e), sanitize(e.getMessage()));
                throw toServiceException("基金全量同步分区失败", e);
            }
        });
    }

    @Override
    public FundSyncRunVo runIncremental() {
        ensureSyncEnabled();
        String batchId = newBatchId("incremental");
        FundSyncRun run = createRun(FundDatasetEnum.FUND_CATALOG.getCode(), "ALL", "incremental", null, batchId);
        RLock lock = RedisUtils.getClient().getLock(FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "incremental");
        return withLock(lock, () -> {
            try {
                FundSyncEnvelope<FundProviderResponse> envelope = withRateLimitAndRetry(run,
                    () -> providerClient.syncCatalog(batchId, 1, properties.getCatalogPageSize()));
                applyMeta(run, envelope.getMeta());
                PersistResult result = persistCatalog(run, envelope.getRecords(), envelope);
                int failedFunds = 0;
                for (FundProviderResponse record : safe(envelope.getRecords())) {
                    if (!isFundCode(record.getFundCode())) {
                        continue;
                    }
                    try {
                        triggerFundSync(record.getFundCode(), properties.getIncrementalNavDays());
                    } catch (ServiceException ignored) {
                        // 子运行已经保存失败详情；父运行只汇总失败基金数量，继续处理其他基金。
                        failedFunds++;
                    }
                }
                String state = failedFunds > 0 ? FundSyncStatusEnum.PARTIAL_SUCCESS.getCode() : result.state();
                run.setFailedCount(failedFunds);
                finishRun(run, state, null, null, failedFunds > 0 ? failedFunds + " 只基金增量同步失败" : null);
                return requireRunVo(run.getId());
            } catch (Exception e) {
                finishRun(run, FundSyncStatusEnum.FAILED.getCode(), run.getCursorValue(), errorCode(e), sanitize(e.getMessage()));
                throw toServiceException("基金增量同步失败", e);
            }
        });
    }

    @Override
    public FundSyncRunVo submitFullHistorySync() {
        return submitGlobalNavSync(FULL_HISTORY_SCOPE, true);
    }

    @Override
    public FundSyncRunVo submitLatestNavContinuation() {
        return submitGlobalNavSync(CONTINUE_FROM_LATEST_NAV_SCOPE, false);
    }

    private FundSyncRunVo submitGlobalNavSync(String scopeValue, boolean fullHistory) {
        ensureSyncEnabled();
        RLock submissionLock = RedisUtils.getClient().getLock(
            FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "global-nav:submit"
        );
        return withLock(submissionLock, () -> {
            FundSyncRunVo latest = fundSyncRunMapper.selectLatestGlobalNavRun();
            if (latest != null && isResumableGlobalNavState(latest.getState())) {
                return resumeGlobalNavSyncLocked(latest);
            }
            String batchId = newBatchId(fullHistory ? "full_history" : "continue_nav");
            FundSyncRun run = createRun(FundDatasetEnum.FUND_NAV.getCode(), "ALL", scopeValue, null, batchId);
            invalidateSyncStatusCache();
            try {
                scheduledExecutorService.execute(() -> runGlobalNavSync(run.getId(), fullHistory));
            } catch (RuntimeException e) {
                finishRun(run, FundSyncStatusEnum.FAILED.getCode(), null, errorCode(e), sanitize(e.getMessage()));
                throw toServiceException("全局基金同步任务提交失败", e);
            }
            return requireRunVo(run.getId());
        });
    }

    @Override
    public FundSyncRunVo pauseGlobalNavSync() {
        RLock submissionLock = RedisUtils.getClient().getLock(
            FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "global-nav:submit"
        );
        return withLock(submissionLock, () -> {
            FundSyncRunVo latest = fundSyncRunMapper.selectLatestGlobalNavRun();
            if (latest == null) {
                throw new ServiceException("当前没有可暂停的全量净值同步任务");
            }
            int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
                .eq(FundSyncRun::getId, latest.getId())
                .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
                .set(FundSyncRun::getState, FundSyncStatusEnum.PAUSED.getCode())
                .set(FundSyncRun::getErrorCode, null)
                .set(FundSyncRun::getErrorMessage, "已由用户暂停，可从当前游标继续"));
            if (updated != 1) {
                throw new ServiceException("仅运行中的全量净值同步任务可以暂停");
            }
            invalidateSyncStatusCache();
            return requireRunVo(latest.getId());
        });
    }

    @Override
    public FundSyncRunVo resumeGlobalNavSync() {
        ensureSyncEnabled();
        RLock submissionLock = RedisUtils.getClient().getLock(
            FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "global-nav:submit"
        );
        return withLock(submissionLock, () -> {
            FundSyncRunVo latest = fundSyncRunMapper.selectLatestGlobalNavRun();
            if (latest == null) {
                throw new ServiceException("当前没有可继续的全量净值同步任务");
            }
            return resumeGlobalNavSyncLocked(latest);
        });
    }

    private FundSyncRunVo resumeGlobalNavSyncLocked(FundSyncRunVo run) {
        boolean resumedFromPause = FundSyncStatusEnum.PAUSED.getCode().equals(run.getState());
        if (resumedFromPause) {
            int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
                .eq(FundSyncRun::getId, run.getId())
                .eq(FundSyncRun::getState, FundSyncStatusEnum.PAUSED.getCode())
                .set(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
                .set(FundSyncRun::getErrorCode, null)
                .set(FundSyncRun::getErrorMessage, null));
            if (updated != 1) {
                throw new ServiceException("全量净值同步状态已变化，请刷新后重试");
            }
            invalidateSyncStatusCache();
        } else if (!FundSyncStatusEnum.RUNNING.getCode().equals(run.getState())) {
            throw new ServiceException("仅已暂停或中断的全量净值同步任务可以继续");
        }

        RLock runLock = RedisUtils.getClient().getLock(FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "global-nav:run");
        if (!runLock.isLocked()) {
            try {
                scheduledExecutorService.execute(() -> runGlobalNavSync(
                    run.getId(),
                    FULL_HISTORY_SCOPE.equals(run.getScopeValue())
                ));
            } catch (RuntimeException e) {
                if (resumedFromPause) {
                    fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
                        .eq(FundSyncRun::getId, run.getId())
                        .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
                        .set(FundSyncRun::getState, FundSyncStatusEnum.PAUSED.getCode())
                        .set(FundSyncRun::getErrorMessage, "继续同步任务提交失败，请稍后重试"));
                    invalidateSyncStatusCache();
                }
                throw toServiceException("全量净值同步任务继续提交失败", e);
            }
        }
        return requireRunVo(run.getId());
    }

    private void runGlobalNavSync(Long runId, boolean fullHistory) {
        RLock runLock = RedisUtils.getClient().getLock(FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "global-nav:run");
        try {
            withLock(runLock, () -> {
                FundSyncRun run = fundSyncRunMapper.selectById(runId);
                if (run == null || !FundSyncStatusEnum.RUNNING.getCode().equals(run.getState())) {
                    return null;
                }
                int rejected = run.getRejectedCount() == null ? 0 : run.getRejectedCount();
                markLegacyGlobalNavCatalogCompleted(run);
                if (fullHistory && !GLOBAL_NAV_CATALOG_DONE.equals(run.getPartitionKey())) {
                    Integer catalogRejected = syncGlobalCatalog(run);
                    if (catalogRejected == null || !markGlobalCatalogCompleted(run, catalogRejected)) {
                        return null;
                    }
                    rejected = catalogRejected;
                    if (!waitForProviderRateWindow(run.getId())) {
                        return null;
                    }
                }
                syncAllFundNavs(run, fullHistory, rejected);
                return null;
            });
        } catch (Exception e) {
            FundSyncRun run = fundSyncRunMapper.selectById(runId);
            if (run != null && FundSyncStatusEnum.RUNNING.getCode().equals(run.getState())) {
                finishRun(run, FundSyncStatusEnum.FAILED.getCode(), run.getCursorValue(), errorCode(e), sanitize(e.getMessage()));
            }
            log.error("全局基金净值同步运行 {} 失败", runId, e);
        }
    }

    private Integer syncGlobalCatalog(FundSyncRun run) {
        int page = globalCatalogPage(run.getPartitionKey());
        int rejected = run.getRejectedCount() == null ? 0 : run.getRejectedCount();
        while (true) {
            if (!isGlobalNavRunRunning(run.getId())) {
                return null;
            }
            int requestedPage = page;
            FundSyncEnvelope<FundProviderResponse> envelope = withRateLimitAndRetry(run,
                () -> providerClient.syncCatalog(run.getFetchBatchId(), requestedPage, properties.getCatalogPageSize()));
            applyMeta(run, envelope.getMeta());
            PersistResult catalogResult = persistCatalog(run, envelope.getRecords(), envelope, false);
            if (!isGlobalNavRunRunning(run.getId())) {
                return null;
            }
            rejected += catalogResult.rejectedCount();

            FundSyncBatchMetaDto meta = envelope.getMeta();
            if (meta == null || !Boolean.TRUE.equals(meta.getHasMore()) || meta.getNextPage() == null) {
                return rejected;
            }
            page = meta.getNextPage();
            if (!updateGlobalCatalogProgress(run, page, rejected)) {
                return null;
            }
        }
    }

    private void syncAllFundNavs(FundSyncRun run, boolean fullHistory, int rejected) {
        String cursor = run.getCursorValue();
        int success = cursor == null || cursor.isBlank() ? 0 : (run.getSuccessCount() == null ? 0 : run.getSuccessCount());
        int failed = run.getFailedCount() == null ? 0 : run.getFailedCount();
        int batchSize = Math.max(1, properties.getCatalogPageSize());
        LocalDate endDate = LocalDate.now();
        while (true) {
            if (!isGlobalNavRunRunning(run.getId())) {
                return;
            }
            List<String> fundCodes = fundInfoMapper.selectActiveFundCodesAfter(cursor, batchSize);
            if (fundCodes.isEmpty()) {
                break;
            }
            for (String fundCode : fundCodes) {
                if (!isGlobalNavRunRunning(run.getId())) {
                    return;
                }
                FundSyncRunVo childRun = null;
                try {
                    childRun = fullHistory
                        ? syncFundNavOnly(fundCode, null, endDate)
                        : syncFundFromLatestNav(fundCode, endDate);
                    success++;
                    if (childRun != null && !FundSyncStatusEnum.SUCCESS.getCode().equals(childRun.getState())) {
                        rejected++;
                    }
                } catch (ServiceException e) {
                    failed++;
                    log.warn("全局基金净值同步跳过失败基金 {}: {}", fundCode, sanitize(e.getMessage()));
                }
                cursor = fundCode;
                if (!updateGlobalProgress(run, cursor, success, rejected, failed)) {
                    return;
                }
                if (childRun != null && !waitForGlobalNavPace(run.getId())) {
                    return;
                }
            }
            if (fundCodes.size() < batchSize) {
                break;
            }
        }
        if (!isGlobalNavRunRunning(run.getId())) {
            return;
        }
        String message = failed > 0 ? failed + " 只基金同步失败，已保留游标 " + cursor : null;
        finishRun(run, resolveState(success, rejected, failed), cursor, null, message);
    }

    private FundSyncRunVo syncFundFromLatestNav(String fundCode, LocalDate endDate) {
        FundNavPointVo latest = fundNavMapper.selectLatest(fundCode);
        if (latest != null && latest.getDate() != null && !latest.getDate().isBefore(endDate)) {
            return null;
        }
        LocalDate startDate = latest == null || latest.getDate() == null ? null : latest.getDate().plusDays(1);
        return syncFundNavOnly(fundCode, startDate, endDate);
    }

    private boolean updateGlobalProgress(FundSyncRun run, String cursor, int success, int rejected, int failed) {
        run.setCursorValue(cursor);
        run.setSuccessCount(success);
        run.setRejectedCount(rejected);
        run.setFailedCount(failed);
        int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
            .eq(FundSyncRun::getId, run.getId())
            .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
            .set(FundSyncRun::getCursorValue, cursor)
            .set(FundSyncRun::getSuccessCount, success)
            .set(FundSyncRun::getRejectedCount, rejected)
            .set(FundSyncRun::getFailedCount, failed));
        if (updated == 1) {
            invalidateSyncStatusCache();
        }
        return updated == 1;
    }

    private boolean markGlobalCatalogCompleted(FundSyncRun run, int rejected) {
        int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
            .eq(FundSyncRun::getId, run.getId())
            .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
            .set(FundSyncRun::getPartitionKey, GLOBAL_NAV_CATALOG_DONE)
            .set(FundSyncRun::getCursorValue, null)
            .set(FundSyncRun::getSuccessCount, 0)
            .set(FundSyncRun::getRejectedCount, rejected)
            .set(FundSyncRun::getFailedCount, 0));
        if (updated == 1) {
            run.setPartitionKey(GLOBAL_NAV_CATALOG_DONE);
            run.setCursorValue(null);
            run.setSuccessCount(0);
            run.setRejectedCount(rejected);
            run.setFailedCount(0);
            invalidateSyncStatusCache();
        }
        return updated == 1;
    }

    /**
     * 兼容目录完成标记上线前已进入逐基金净值阶段的任务，避免恢复时重复目录阶段并清空展示进度。
     */
    private void markLegacyGlobalNavCatalogCompleted(FundSyncRun run) {
        if (!FULL_HISTORY_SCOPE.equals(run.getScopeValue())
            || run.getCursorValue() == null
            || run.getCursorValue().isBlank()
            || (run.getPartitionKey() != null && !run.getPartitionKey().isBlank())) {
            return;
        }
        int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
            .eq(FundSyncRun::getId, run.getId())
            .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
            .and(wrapper -> wrapper.isNull(FundSyncRun::getPartitionKey)
                .or()
                .eq(FundSyncRun::getPartitionKey, ""))
            .set(FundSyncRun::getPartitionKey, GLOBAL_NAV_CATALOG_DONE));
        if (updated == 1) {
            run.setPartitionKey(GLOBAL_NAV_CATALOG_DONE);
            invalidateSyncStatusCache();
        }
    }

    private boolean updateGlobalCatalogProgress(FundSyncRun run, int nextPage, int rejected) {
        String partitionKey = GLOBAL_NAV_CATALOG_PAGE_PREFIX + nextPage;
        int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
            .eq(FundSyncRun::getId, run.getId())
            .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
            .set(FundSyncRun::getPartitionKey, partitionKey)
            .set(FundSyncRun::getCursorValue, null)
            .set(FundSyncRun::getSuccessCount, 0)
            .set(FundSyncRun::getRejectedCount, rejected)
            .set(FundSyncRun::getFailedCount, 0));
        if (updated == 1) {
            run.setPartitionKey(partitionKey);
            run.setCursorValue(null);
            run.setSuccessCount(0);
            run.setRejectedCount(rejected);
            run.setFailedCount(0);
            invalidateSyncStatusCache();
        }
        return updated == 1;
    }

    private int globalCatalogPage(String partitionKey) {
        if (partitionKey == null || !partitionKey.startsWith(GLOBAL_NAV_CATALOG_PAGE_PREFIX)) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(partitionKey.substring(GLOBAL_NAV_CATALOG_PAGE_PREFIX.length())));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private boolean isGlobalNavRunRunning(Long runId) {
        FundSyncRun run = fundSyncRunMapper.selectById(runId);
        return run != null && FundSyncStatusEnum.RUNNING.getCode().equals(run.getState());
    }

    private boolean isResumableGlobalNavState(String state) {
        return FundSyncStatusEnum.RUNNING.getCode().equals(state)
            || FundSyncStatusEnum.PAUSED.getCode().equals(state);
    }

    private boolean waitForGlobalNavPace(Long runId) {
        long rate = Math.max(1, properties.getProviderRatePerMinute());
        long intervalMillis = Math.max(1L,
            (GLOBAL_NAV_REQUESTS_PER_FUND * 60_000L + rate - 1) / rate);
        long remainingMillis = intervalMillis;
        while (remainingMillis > 0) {
            if (!isGlobalNavRunRunning(runId)) {
                return false;
            }
            long sleepMillis = Math.min(TimeUnit.SECONDS.toMillis(1), remainingMillis);
            try {
                TimeUnit.MILLISECONDS.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException("全局基金同步任务被中断");
            }
            remainingMillis -= sleepMillis;
        }
        return isGlobalNavRunRunning(runId);
    }

    private boolean waitForProviderRateWindow(Long runId) {
        for (int second = 0; second < PROVIDER_RATE_INTERVAL_SECONDS; second++) {
            if (!isGlobalNavRunRunning(runId)) {
                return false;
            }
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ServiceException("全局基金同步任务被中断");
            }
        }
        return isGlobalNavRunRunning(runId);
    }

    @Override
    public FundSyncRunVo triggerFundSync(String fundCode, int days) {
        ensureSyncEnabled();
        String normalizedCode = normalizeFundCode(fundCode);
        int requestedDays = normalizeDays(days);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = requestedDays == 0 ? null : endDate.minusDays(requestedDays);
        return triggerFundSyncRange(normalizedCode, startDate, endDate);
    }

    @Override
    public FundSyncRunVo triggerFundSync(String fundCode, LocalDate startDate, LocalDate endDate) {
        ensureSyncEnabled();
        String normalizedCode = normalizeFundCode(fundCode);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ServiceException("开始日期不能晚于结束日期");
        }
        return triggerFundSyncRange(normalizedCode, startDate, endDate);
    }

    private FundSyncRunVo triggerFundSyncRange(String fundCode, LocalDate startDate, LocalDate endDate) {
        RLock lock = RedisUtils.getClient().getLock(FundCacheConstants.SYNC_FUND_LOCK_PREFIX + fundCode);
        return withLock(lock, () -> syncSingleFund(fundCode, startDate, endDate));
    }

    @Override
    public List<org.dromara.fund.domain.vo.FundHoldingVo> queryLatestHoldings(String fundCode, LocalDate reportDate) {
        String normalizedCode = normalizeFundCode(fundCode);
        if (reportDate != null) {
            return fundHoldingMapper.selectByReportDate(normalizedCode, reportDate);
        }
        return fundHoldingMapper.selectLatest(normalizedCode);
    }

    @Override
    public TableDataInfo<org.dromara.fund.domain.vo.FundSyncRunVo> queryRunPage(FundSyncRunQueryBo bo, PageQuery pageQuery) {
        bo.setDataset(normalizeDataset(bo.getDataset()));
        Page<org.dromara.fund.domain.vo.FundSyncRunVo> page = fundSyncRunMapper.selectRunPage(pageQuery.build(), bo);
        return TableDataInfo.build(page);
    }

    @Override
    public org.dromara.fund.domain.vo.FundSyncRunVo queryRunDetail(Long id) {
        return requireRunVo(id);
    }

    @Override
    public FundSyncRunVo retryRun(Long id) {
        FundSyncRunVo previous = requireRunVo(id);
        if (!FundSyncStatusEnum.FAILED.getCode().equals(previous.getState())
            && !FundSyncStatusEnum.PARTIAL_SUCCESS.getCode().equals(previous.getState())) {
            throw new ServiceException("仅失败或部分成功的同步运行可以重试");
        }
        if ("FUND_CODE".equals(previous.getScopeType()) && isFundCode(previous.getScopeValue())) {
            return triggerFundSync(previous.getScopeValue(), 366);
        }
        if (FULL_HISTORY_SCOPE.equals(previous.getSyncType())) {
            return submitFullHistorySync();
        }
        if (CONTINUE_FROM_LATEST_NAV_SCOPE.equals(previous.getSyncType())) {
            return submitLatestNavContinuation();
        }
        if ("INCREMENTAL".equals(previous.getSyncType())) {
            return runIncremental();
        }
        return runFullInitPartition(previous.getCursorValue());
    }

    @Override
    public FundSyncStatusSummaryVo queryStatus(String dataset, String scopeType, String scopeValue) {
        dataset = normalizeDataset(dataset);
        String key = FundCacheConstants.SYNC_STATUS_KEY_PREFIX
            + nullToAll(dataset) + ":" + nullToAll(scopeType) + ":" + nullToAll(scopeValue);
        FundSyncStatusSummaryVo cached = RedisUtils.getCacheObject(key);
        if (cached != null) {
            return cached;
        }
        FundSyncStatusSummaryVo status = fundSyncRunMapper.selectLatestStatus(dataset, scopeType, scopeValue);
        if (status != null) {
            RedisUtils.setCacheObject(key, status, properties.getSyncStatusCacheTtl());
        }
        return status;
    }

    @Override
    public FundGlobalNavSyncStatusVo queryGlobalNavStatus() {
        FundSyncRunVo run = fundSyncRunMapper.selectLatestGlobalNavRun();
        FundGlobalNavSyncStatusVo result = new FundGlobalNavSyncStatusVo();
        result.setTotalFundCount(fundInfoMapper.countActiveFundCodes());
        if (run == null) {
            result.setState("IDLE");
            return result;
        }

        result.setId(run.getId());
        result.setRunId(run.getRunId());
        result.setSyncType(run.getSyncType());
        result.setCursorValue(run.getCursorValue());
        result.setSuccessCount(run.getSuccessCount());
        result.setRejectedCount(run.getRejectedCount());
        result.setFailedCount(run.getFailedCount());
        result.setStartedAt(run.getStartedAt());
        result.setFinishedAt(run.getFinishedAt());
        result.setErrorMessage(run.getErrorMessage());
        if (run.getCursorValue() != null && !run.getCursorValue().isBlank()) {
            result.setProcessedFundCount(fundInfoMapper.countActiveFundCodesThrough(run.getCursorValue()));
        }

        String state = run.getState();
        if (FundSyncStatusEnum.PAUSED.getCode().equals(state)) {
            result.setResumable(true);
        } else if (FundSyncStatusEnum.RUNNING.getCode().equals(state)) {
            RLock runLock = RedisUtils.getClient().getLock(FundCacheConstants.SYNC_GLOBAL_LOCK_PREFIX + "global-nav:run");
            if (!runLock.isLocked()) {
                state = "INTERRUPTED";
                result.setResumable(true);
                if (result.getErrorMessage() == null || result.getErrorMessage().isBlank()) {
                    result.setErrorMessage("后台服务已重启，任务尚未继续执行");
                }
            }
        }
        result.setState(state);
        return result;
    }

    @Override
    public TableDataInfo<org.dromara.fund.domain.vo.FundDataQualityIssueVo> queryIssuePage(
        FundDataQualityIssueQueryBo bo,
        PageQuery pageQuery
    ) {
        bo.setDataset(normalizeDataset(bo.getDataset()));
        Page<org.dromara.fund.domain.vo.FundDataQualityIssueVo> page =
            qualityIssueMapper.selectIssuePage(pageQuery.build(), bo);
        return TableDataInfo.build(page);
    }

    private FundSyncRunVo syncSingleFund(String fundCode, LocalDate startDate, LocalDate endDate) {
        String batchId = newBatchId("fund_" + fundCode);
        FundSyncRun run = createRun(FundDatasetEnum.FUND_INFO.getCode(), "FUND_CODE", fundCode, null, batchId);
        try {
            FundSyncEnvelope<FundProviderResponse> profile = withRateLimitAndRetry(run,
                () -> providerClient.syncFund(fundCode, batchId));
            FundSyncEnvelope<FundNavProviderResponse> nav = withRateLimitAndRetry(run,
                () -> providerClient.syncNav(fundCode, startDate, endDate, batchId));
            FundSyncEnvelope<FundHoldingProviderResponse> holding = withRateLimitAndRetry(run,
                () -> providerClient.syncHoldings(fundCode, null, batchId));
            applyMeta(run, firstMeta(profile, nav, holding));
            PersistResult result = persistFundBundle(run, profile, nav, holding);
            finishRun(run, result.state(), null, null, null);
            return requireRunVo(run.getId());
        } catch (Exception e) {
            finishRun(run, FundSyncStatusEnum.FAILED.getCode(), run.getCursorValue(), errorCode(e), sanitize(e.getMessage()));
            throw toServiceException("基金同步失败", e);
        }
    }

    /** 仅同步确认净值，供全量历史与按最新净值续拉使用，避免无关的档案和持仓上游请求拖慢任务。 */
    private FundSyncRunVo syncFundNavOnly(String fundCode, LocalDate startDate, LocalDate endDate) {
        String batchId = newBatchId("nav_" + fundCode);
        FundSyncRun run = createRun(FundDatasetEnum.FUND_NAV.getCode(), "FUND_CODE", fundCode, null, batchId);
        try {
            FundSyncEnvelope<FundNavProviderResponse> nav = withRateLimitAndRetry(run,
                () -> providerClient.syncNav(fundCode, startDate, endDate, batchId));
            applyMeta(run, nav.getMeta());
            PersistResult result = persistFundNav(run, nav);
            finishRun(run, result.state(), null, null, null);
            return requireRunVo(run.getId());
        } catch (Exception e) {
            finishRun(run, FundSyncStatusEnum.FAILED.getCode(), run.getCursorValue(), errorCode(e), sanitize(e.getMessage()));
            throw toServiceException("基金净值同步失败", e);
        }
    }

    private PersistResult persistCatalog(
        FundSyncRun run,
        List<FundProviderResponse> records,
        FundSyncEnvelope<FundProviderResponse> envelope
    ) {
        return persistCatalog(run, records, envelope, true);
    }

    private PersistResult persistCatalog(
        FundSyncRun run,
        List<FundProviderResponse> records,
        FundSyncEnvelope<FundProviderResponse> envelope,
        boolean updateSyncCounts
    ) {
        FundSyncBatchMetaDto meta = envelope == null ? null : envelope.getMeta();
        List<FundInfo> valid = new ArrayList<>();
        List<FundDataQualityIssue> issues = new ArrayList<>();
        for (FundProviderResponse record : safe(records)) {
            if (!isFundCode(record.getFundCode())) {
                issues.add(issue(run, FundDatasetEnum.FUND_CATALOG.getCode(), record.getFundCode(),
                    FundQualityReasonCodeEnum.INVALID_FUND_CODE.getCode(), "invalid catalog code"));
                continue;
            }
            valid.add(toFundInfo(record, meta));
        }
        appendProviderIssues(run, envelope, issues);
        return transactionTemplate.execute(status -> {
            int changed = 0;
            for (FundInfo item : valid) {
                changed += fundInfoMapper.upsert(item);
            }
            for (FundDataQualityIssue issue : issues) {
                qualityIssueMapper.upsert(issue);
            }
            run.setCacheInvalidatedCount(changed > 0 ? valid.stream().map(FundInfo::getFundCode).collect(java.util.stream.Collectors.toSet()).size() : 0);
            if (updateSyncCounts) {
                updateCounts(run, valid.size(), issues.size(), 0);
            }
            if (changed > 0) {
                afterCommitInvalidate(valid.stream().map(FundInfo::getFundCode).toList());
            }
            return new PersistResult(resolveState(valid.size(), issues.size(), 0), changed, issues.size());
        });
    }

    private PersistResult persistFundBundle(
        FundSyncRun run,
        FundSyncEnvelope<FundProviderResponse> profile,
        FundSyncEnvelope<FundNavProviderResponse> nav,
        FundSyncEnvelope<FundHoldingProviderResponse> holding
    ) {
        List<FundDataQualityIssue> issues = new ArrayList<>();
        List<FundInfo> fundInfos = toFundInfos(run, profile, issues);
        List<FundNav> navItems = toFundNavs(run, nav, issues);
        List<FundHolding> holdingItems = toHoldings(run, holding, issues);
        appendProviderIssues(run, profile, issues);
        appendProviderIssues(run, nav, issues);
        appendProviderIssues(run, holding, issues);

        return transactionTemplate.execute(status -> {
            int changed = 0;
            for (FundInfo item : fundInfos) {
                changed += fundInfoMapper.upsert(item);
            }
            if (!navItems.isEmpty()) {
                changed += fundNavMapper.upsertBatch(navItems);
            }
            if (!holdingItems.isEmpty()) {
                changed += fundHoldingMapper.upsertBatch(holdingItems);
            }
            for (FundDataQualityIssue issue : issues) {
                qualityIssueMapper.upsert(issue);
            }
            if (changed > 0) {
                Set<String> fundCodes = new LinkedHashSet<>();
                fundInfos.forEach(item -> fundCodes.add(item.getFundCode()));
                navItems.forEach(item -> fundCodes.add(item.getFundCode()));
                holdingItems.forEach(item -> fundCodes.add(item.getFundCode()));
                run.setCacheInvalidatedCount(fundCodes.size());
                updateCounts(run, fundInfos.size() + navItems.size() + holdingItems.size(), issues.size(), 0);
                afterCommitInvalidate(fundCodes);
            } else {
                run.setCacheInvalidatedCount(0);
                updateCounts(run, fundInfos.size() + navItems.size() + holdingItems.size(), issues.size(), 0);
            }
            return new PersistResult(resolveState(fundInfos.size() + navItems.size() + holdingItems.size(), issues.size(), 0), changed, issues.size());
        });
    }

    private PersistResult persistFundNav(FundSyncRun run, FundSyncEnvelope<FundNavProviderResponse> nav) {
        List<FundDataQualityIssue> issues = new ArrayList<>();
        List<FundNav> navItems = toFundNavs(run, nav, issues);
        appendProviderIssues(run, nav, issues);
        return transactionTemplate.execute(status -> {
            int changed = navItems.isEmpty() ? 0 : fundNavMapper.upsertBatch(navItems);
            for (FundDataQualityIssue issue : issues) {
                qualityIssueMapper.upsert(issue);
            }
            run.setCacheInvalidatedCount(navItems.isEmpty() ? 0 : 1);
            updateCounts(run, navItems.size(), issues.size(), 0);
            if (!navItems.isEmpty()) {
                afterCommitInvalidate(List.of(navItems.getFirst().getFundCode()));
            }
            return new PersistResult(resolveState(navItems.size(), issues.size(), 0), changed, issues.size());
        });
    }

    private List<FundInfo> toFundInfos(FundSyncRun run, FundSyncEnvelope<FundProviderResponse> envelope, List<FundDataQualityIssue> issues) {
        FundSyncBatchMetaDto meta = envelope == null ? null : envelope.getMeta();
        List<FundInfo> result = new ArrayList<>();
        for (FundProviderResponse record : safe(envelope == null ? null : envelope.getRecords())) {
            if (!isFundCode(record.getFundCode())) {
                issues.add(issue(run, FundDatasetEnum.FUND_INFO.getCode(), record.getFundCode(),
                    FundQualityReasonCodeEnum.INVALID_FUND_CODE.getCode(), "invalid profile code"));
                continue;
            }
            result.add(toFundInfo(record, meta));
        }
        return result;
    }

    private List<FundNav> toFundNavs(FundSyncRun run, FundSyncEnvelope<FundNavProviderResponse> envelope, List<FundDataQualityIssue> issues) {
        FundSyncBatchMetaDto meta = envelope == null ? null : envelope.getMeta();
        List<FundNav> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (FundNavProviderResponse record : safe(envelope == null ? null : envelope.getRecords())) {
            String recordKey = record.getFundCode() + ":" + record.getDate();
            if (!isFundCode(record.getFundCode())) {
                issues.add(issue(run, FundDatasetEnum.FUND_NAV.getCode(), recordKey,
                    FundQualityReasonCodeEnum.INVALID_FUND_CODE.getCode(), "invalid nav fund code"));
                continue;
            }
            if (record.getDate() == null || record.getDate().isAfter(today)) {
                issues.add(issue(run, FundDatasetEnum.FUND_NAV.getCode(), recordKey,
                    FundQualityReasonCodeEnum.FUTURE_BUSINESS_DATE.getCode(), "invalid nav date"));
                continue;
            }
            if (record.getNav() == null || record.getNav().compareTo(BigDecimal.ZERO) <= 0) {
                issues.add(issue(run, FundDatasetEnum.FUND_NAV.getCode(), recordKey,
                    FundQualityReasonCodeEnum.INVALID_NAV.getCode(), "invalid unit nav"));
                continue;
            }
            result.add(toFundNav(record, meta));
        }
        return result;
    }

    private List<FundHolding> toHoldings(
        FundSyncRun run,
        FundSyncEnvelope<FundHoldingProviderResponse> envelope,
        List<FundDataQualityIssue> issues
    ) {
        FundSyncBatchMetaDto meta = envelope == null ? null : envelope.getMeta();
        List<FundHolding> result = new ArrayList<>();
        for (FundHoldingProviderResponse record : safe(envelope == null ? null : envelope.getRecords())) {
            LocalDate reportDate = parseReportDate(record);
            String recordKey = record.getFundCode() + ":" + reportDate + ":" + record.getStockCode();
            if (!isFundCode(record.getFundCode())) {
                issues.add(issue(run, FundDatasetEnum.FUND_HOLDING.getCode(), recordKey,
                    FundQualityReasonCodeEnum.INVALID_FUND_CODE.getCode(), "invalid holding fund code"));
                continue;
            }
            if (reportDate == null || reportDate.isAfter(LocalDate.now())) {
                issues.add(issue(run, FundDatasetEnum.FUND_HOLDING.getCode(), recordKey,
                    FundQualityReasonCodeEnum.FUTURE_BUSINESS_DATE.getCode(), "invalid holding report date"));
                continue;
            }
            BigDecimal weight = record.getWeight();
            if (weight == null || weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(new BigDecimal("100")) > 0) {
                issues.add(issue(run, FundDatasetEnum.FUND_HOLDING.getCode(), recordKey,
                    FundQualityReasonCodeEnum.INVALID_HOLDING_RATIO.getCode(), "invalid disclosed holding weight"));
                continue;
            }
            result.add(toFundHolding(record, reportDate, meta));
        }
        return result;
    }

    private FundInfo toFundInfo(FundProviderResponse source, FundSyncBatchMetaDto meta) {
        FundInfo target = new FundInfo();
        target.setId(IdGeneratorUtil.nextLongId());
        target.setFundCode(source.getFundCode());
        target.setFundName(source.getFundName());
        target.setFundType(blankToDefault(source.getFundType(), "未知类型"));
        target.setPinyinAbbr(source.getPinyinAbbr());
        target.setManagerName(source.getManagerName());
        target.setCustodianName(source.getCustodianName());
        target.setEstablishDate(source.getEstablishDate());
        target.setBenchmark(source.getBenchmark());
        target.setRiskLevel(source.getRiskLevel());
        target.setFundScale(source.getFundScale());
        target.setStatus(blankToDefault(source.getStatus(), "0"));
        target.setSource(blankToDefault(source.getSource(), meta == null ? null : meta.getSource(), "AKSHARE"));
        target.setSourceUpdatedAt(firstNonNull(source.getSourceTime(), meta == null ? null : meta.getSourceTime(), OffsetDateTime.now()));
        target.setBusinessDate(LocalDate.now());
        target.setFetchBatchId(meta == null ? null : meta.getBatchId());
        target.setDataVersion(firstNonBlank(source.getDataVersion(), meta == null ? null : meta.getDataVersion(), versionFromBatch(meta)));
        target.setChecksum(firstNonBlank(source.getChecksum(), stableChecksum(source.toString())));
        target.setQualityStatus(blankToDefault(source.getQualityStatus(), meta == null ? null : meta.getQualityStatus(),
            FundDataQualityStatusEnum.NORMAL.getCode()));
        target.setDelFlag(0L);
        return target;
    }

    private FundNav toFundNav(FundNavProviderResponse source, FundSyncBatchMetaDto meta) {
        FundNav target = new FundNav();
        target.setId(IdGeneratorUtil.nextLongId());
        target.setFundCode(source.getFundCode());
        target.setNavDate(source.getDate());
        target.setUnitNav(source.getNav());
        target.setAccumulatedNav(source.getAccumulatedNav());
        target.setDailyGrowthRate(source.getGrowthRate());
        target.setSource(blankToDefault(source.getSource(), meta == null ? null : meta.getSource(), "AKSHARE"));
        target.setSourceTime(firstNonNull(source.getSourceTime(), meta == null ? null : meta.getSourceTime(), OffsetDateTime.now()));
        target.setFetchBatchId(meta == null ? null : meta.getBatchId());
        target.setDataVersion(firstNonBlank(source.getDataVersion(), meta == null ? null : meta.getDataVersion(), versionFromBatch(meta)));
        target.setChecksum(firstNonBlank(source.getChecksum(), stableChecksum(source.toString())));
        target.setQualityStatus(blankToDefault(source.getQualityStatus(), meta == null ? null : meta.getQualityStatus(),
            FundDataQualityStatusEnum.NORMAL.getCode()));
        return target;
    }

    private FundHolding toFundHolding(FundHoldingProviderResponse source, LocalDate reportDate, FundSyncBatchMetaDto meta) {
        FundHolding target = new FundHolding();
        target.setId(IdGeneratorUtil.nextLongId());
        target.setFundCode(source.getFundCode());
        target.setReportDate(reportDate);
        target.setStockCode(source.getStockCode());
        target.setStockName(source.getStockName());
        target.setDisclosedWeight(source.getWeight());
        target.setHoldingRank(source.getRank());
        target.setSource(blankToDefault(source.getSource(), meta == null ? null : meta.getSource(), "AKSHARE"));
        target.setSourceTime(firstNonNull(source.getSourceTime(), meta == null ? null : meta.getSourceTime(), OffsetDateTime.now()));
        target.setFetchBatchId(meta == null ? null : meta.getBatchId());
        target.setDataVersion(firstNonBlank(source.getDataVersion(), meta == null ? null : meta.getDataVersion(), versionFromBatch(meta)));
        target.setChecksum(firstNonBlank(source.getChecksum(), stableChecksum(source.toString())));
        target.setQualityStatus(blankToDefault(source.getQualityStatus(), meta == null ? null : meta.getQualityStatus(),
            FundDataQualityStatusEnum.NORMAL.getCode()));
        return target;
    }

    private void appendProviderIssues(FundSyncRun run, FundSyncEnvelope<?> envelope, List<FundDataQualityIssue> issues) {
        if (envelope == null || envelope.getIssues() == null) {
            return;
        }
        for (FundProviderQualityIssueDto source : envelope.getIssues()) {
            FundDataQualityIssue issue = new FundDataQualityIssue();
            issue.setId(IdGeneratorUtil.nextLongId());
            issue.setSyncRunId(run.getId());
            issue.setDataset(blankToDefault(source.getDataset(), run.getDataset()));
            issue.setSource(run.getSource());
            issue.setSourceTime(run.getSourceTime());
            issue.setBusinessDate(run.getBusinessDate());
            issue.setFetchBatchId(firstNonBlank(source.getBatchId(), run.getFetchBatchId()));
            issue.setDataVersion(run.getDataVersion());
            issue.setChecksum(stableChecksum(source.toString()));
            issue.setRecordKey(blankToDefault(source.getRecordKey(), "UNKNOWN"));
            issue.setQualityStatus(FundDataQualityStatusEnum.FAILED.getCode());
            issue.setReasonCode(source.getReasonCode());
            issue.setRawSummary(firstNonBlank(source.getRawDigest(), sanitize(source.getMessage())));
            issue.setDetectedAt(firstNonNull(source.getDiscoveredAt(), OffsetDateTime.now()));
            issue.setIssueStatus(FundQualityIssueStatusEnum.OPEN.getCode());
            issues.add(issue);
        }
    }

    private FundDataQualityIssue issue(FundSyncRun run, String dataset, String recordKey, String reasonCode, String summary) {
        FundDataQualityIssue issue = new FundDataQualityIssue();
        issue.setId(IdGeneratorUtil.nextLongId());
        issue.setSyncRunId(run.getId());
        issue.setDataset(dataset);
        issue.setSource(run.getSource());
        issue.setSourceTime(run.getSourceTime());
        issue.setBusinessDate(run.getBusinessDate());
        issue.setFetchBatchId(run.getFetchBatchId());
        issue.setDataVersion(run.getDataVersion());
        issue.setChecksum(stableChecksum(dataset + ":" + recordKey + ":" + reasonCode + ":" + summary));
        issue.setRecordKey(blankToDefault(recordKey, "UNKNOWN"));
        issue.setQualityStatus(FundDataQualityStatusEnum.FAILED.getCode());
        issue.setReasonCode(reasonCode);
        issue.setRawSummary(sanitize(summary));
        issue.setDetectedAt(OffsetDateTime.now());
        issue.setIssueStatus(FundQualityIssueStatusEnum.OPEN.getCode());
        return issue;
    }

    private FundSyncRun createRun(String dataset, String scopeType, String scopeValue, String partitionKey, String batchId) {
        FundSyncRun run = new FundSyncRun();
        run.setId(IdGeneratorUtil.nextLongId());
        run.setDataset(dataset);
        run.setSource("AKSHARE");
        run.setSourceTime(OffsetDateTime.now());
        run.setBusinessDate(LocalDate.now());
        run.setScopeType(scopeType);
        run.setScopeValue(scopeValue);
        run.setPartitionKey(partitionKey);
        run.setState(FundSyncStatusEnum.RUNNING.getCode());
        run.setQualityStatus(FundDataQualityStatusEnum.NORMAL.getCode());
        run.setFetchBatchId(batchId);
        run.setDataVersion("v-" + batchId);
        run.setChecksum(stableChecksum(dataset + ":" + batchId));
        run.setStartedAt(OffsetDateTime.now());
        run.setSuccessCount(0);
        run.setRejectedCount(0);
        run.setFailedCount(0);
        run.setRetryCount(0);
        run.setUpstreamLatencyMs(0L);
        run.setStaleCount(0);
        run.setCacheInvalidatedCount(0);
        fundSyncRunMapper.insert(run);
        return run;
    }

    private void finishRun(FundSyncRun run, String state, String cursorValue, String errorCode, String errorMessage) {
        run.setState(state);
        run.setCursorValue(cursorValue);
        run.setErrorCode(errorCode);
        run.setErrorMessage(errorMessage);
        run.setFinishedAt(OffsetDateTime.now());
        if (run.getStartedAt() != null) {
            run.setDurationMs(java.time.Duration.between(run.getStartedAt(), run.getFinishedAt()).toMillis());
        }
        run.setQualityStatus(switch (state) {
            case "SUCCESS" -> FundDataQualityStatusEnum.NORMAL.getCode();
            case "PARTIAL_SUCCESS" -> FundDataQualityStatusEnum.PARTIAL.getCode();
            case "FAILED" -> FundDataQualityStatusEnum.FAILED.getCode();
            default -> run.getQualityStatus();
        });
        if (isGlobalNavRun(run)) {
            int updated = fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
                .eq(FundSyncRun::getId, run.getId())
                .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
                .set(FundSyncRun::getState, state)
                .set(FundSyncRun::getCursorValue, cursorValue)
                .set(FundSyncRun::getErrorCode, errorCode)
                .set(FundSyncRun::getErrorMessage, errorMessage)
                .set(FundSyncRun::getFinishedAt, run.getFinishedAt())
                .set(FundSyncRun::getDurationMs, run.getDurationMs())
                .set(FundSyncRun::getQualityStatus, run.getQualityStatus())
                .set(FundSyncRun::getSuccessCount, run.getSuccessCount())
                .set(FundSyncRun::getRejectedCount, run.getRejectedCount())
                .set(FundSyncRun::getFailedCount, run.getFailedCount()));
            if (updated != 1) {
                return;
            }
        } else {
            fundSyncRunMapper.updateById(run);
        }
        invalidateSyncStatusCache();
        if ("FUND_CODE".equals(run.getScopeType()) && isFundCode(run.getScopeValue())) {
            // 同步状态属于详情载荷；即使数据内容未变化，也不能继续返回上一轮同步状态的详情缓存。
            RedisUtils.deleteKeys(FundCacheConstants.INFO_KEY_PREFIX + run.getScopeValue() + ":detail:*");
        }
    }

    private void updateCounts(FundSyncRun run, int success, int rejected, int failed) {
        run.setSuccessCount(success);
        run.setRejectedCount(rejected);
        run.setFailedCount(failed);
        if (isGlobalNavRun(run)) {
            updateGlobalProgress(run, run.getCursorValue(), success, rejected, failed);
            return;
        }
        fundSyncRunMapper.updateById(run);
    }

    private void invalidateSyncStatusCache() {
        RedisUtils.deleteKeys(FundCacheConstants.SYNC_STATUS_KEY_PREFIX + "*");
    }

    private boolean isGlobalNavRun(FundSyncRun run) {
        return FundDatasetEnum.FUND_NAV.getCode().equals(run.getDataset()) && "ALL".equals(run.getScopeType());
    }

    private void applyMeta(FundSyncRun run, FundSyncBatchMetaDto meta) {
        if (meta == null) {
            return;
        }
        run.setSource(blankToDefault(meta.getSource(), run.getSource()));
        run.setSourceTime(firstNonNull(meta.getSourceTime(), run.getSourceTime()));
        run.setQualityStatus(blankToDefault(meta.getQualityStatus(), run.getQualityStatus()));
        run.setDataVersion(firstNonBlank(meta.getDataVersion(), run.getDataVersion()));
        run.setChecksum(firstNonBlank(meta.getChecksum(), run.getChecksum()));
        if (meta.getFetchedAt() != null) {
            run.setBusinessDate(meta.getFetchedAt().toLocalDate());
        }
    }

    @SafeVarargs
    private FundSyncBatchMetaDto firstMeta(FundSyncEnvelope<?>... envelopes) {
        for (FundSyncEnvelope<?> envelope : envelopes) {
            if (envelope != null && envelope.getMeta() != null) {
                return envelope.getMeta();
            }
        }
        return null;
    }

    private String resolveState(int success, int rejected, int failed) {
        if (failed > 0 && success == 0) {
            return FundSyncStatusEnum.FAILED.getCode();
        }
        if (rejected > 0 || failed > 0) {
            return FundSyncStatusEnum.PARTIAL_SUCCESS.getCode();
        }
        return FundSyncStatusEnum.SUCCESS.getCode();
    }

    private boolean hasCompleteLocalData(String fundCode, int requestedDays) {
        boolean fundExists = fundInfoMapper.exists(Wrappers.<FundInfo>lambdaQuery()
            .eq(FundInfo::getFundCode, fundCode)
            .eq(FundInfo::getStatus, "0")
            .isNotNull(FundInfo::getManagerName)
            .isNotNull(FundInfo::getEstablishDate)
            .isNotNull(FundInfo::getBenchmark)
            .isNotNull(FundInfo::getFundScale));
        if (!fundExists) {
            return false;
        }
        int coverage = fundNavMapper.selectSyncCoverage(fundCode);
        return requestedDays == 0 ? coverage >= 5000 : coverage >= requestedDays;
    }

    private void afterCommitInvalidate(Iterable<String> fundCodes) {
        Runnable invalidate = () -> {
            RedisUtils.deleteKeys(FundCacheConstants.CATALOG_KEY_PREFIX + "*");
            for (String fundCode : fundCodes) {
                if (fundCode == null) {
                    continue;
                }
                RedisUtils.deleteKeys(FundCacheConstants.INFO_KEY_PREFIX + fundCode + ":*");
                RedisUtils.deleteKeys(FundCacheConstants.NAV_KEY_PREFIX + fundCode + ":*");
                RedisUtils.deleteKeys(FundCacheConstants.HOLDING_KEY_PREFIX + fundCode + ":*");
                RedisUtils.deleteKeys(FundCacheConstants.estimateCachePattern(fundCode));
                RedisUtils.deleteKeys(FundCacheConstants.navPositionCachePattern(fundCode));
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate.run();
                }
            });
        } else {
            invalidate.run();
        }
    }

    private <T> T withLock(RLock lock, Callable<T> action) {
        boolean locked = false;
        try {
            // 不指定固定 lease，交给 Redisson watchdog 自动续期，避免长批任务执行中锁提前失效。
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw new ServiceException("基金同步任务繁忙，请稍后重试");
            }
            return action.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("基金同步任务被中断");
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw toServiceException("基金同步任务执行失败", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private <T> T withRateLimitAndRetry(FundSyncRun run, Callable<T> action) {
        long started = System.nanoTime();
        try {
            return withRetry(run, () -> {
                int rate = Math.max(1, properties.getProviderRatePerMinute());
                long permits = RedisUtils.rateLimiter(
                    FundCacheConstants.PROVIDER_RATE_LIMIT_KEY,
                    RateType.OVERALL,
                    rate,
                    PROVIDER_RATE_INTERVAL_SECONDS,
                    // RedisUtils 的最后一个参数映射为 Redisson 限流器键保留时间，不能小于限流窗口。
                    PROVIDER_RATE_INTERVAL_SECONDS
                );
                if (permits < 0) {
                    throw new ServiceException("基金数据供应方限流中，请稍后重试");
                }
                return action.call();
            });
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            run.setUpstreamLatencyMs((run.getUpstreamLatencyMs() == null ? 0L : run.getUpstreamLatencyMs()) + elapsedMillis);
            if (isGlobalNavRun(run)) {
                fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
                    .eq(FundSyncRun::getId, run.getId())
                    .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
                    .set(FundSyncRun::getUpstreamLatencyMs, run.getUpstreamLatencyMs()));
            } else {
                fundSyncRunMapper.updateById(run);
            }
        }
    }

    private <T> T withRetry(FundSyncRun run, Callable<T> action) {
        int maxAttempts = Math.max(1, properties.getMaxRetryAttempts());
        ServiceException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (FundProviderException e) {
                if (!e.isRetryable() || attempt == maxAttempts) {
                    throw toServiceException("基金数据供应方拒绝请求", e);
                }
                recordRetry(run, attempt);
                sleepBackoff(attempt, e.getRetryAfterSeconds());
            } catch (ServiceException e) {
                last = e;
                if (attempt == maxAttempts) {
                    throw e;
                }
                recordRetry(run, attempt);
                sleepBackoff(attempt, null);
            } catch (Exception e) {
                // 接口响应对调用方保持简洁，但必须保留原始异常，避免供应方故障只能看到泛化 500。
                log.warn("基金数据供应方调用第 {}/{} 次失败", attempt, maxAttempts, e);
                last = toServiceException("基金数据供应方调用失败", e);
                if (attempt == maxAttempts) {
                    throw last;
                }
                recordRetry(run, attempt);
                sleepBackoff(attempt, null);
            }
        }
        throw last == null ? new ServiceException("基金数据供应方调用失败") : last;
    }

    private void recordRetry(FundSyncRun run, int attempt) {
        run.setRetryCount(attempt);
        if (isGlobalNavRun(run)) {
            fundSyncRunMapper.update(null, Wrappers.<FundSyncRun>lambdaUpdate()
                .eq(FundSyncRun::getId, run.getId())
                .eq(FundSyncRun::getState, FundSyncStatusEnum.RUNNING.getCode())
                .set(FundSyncRun::getRetryCount, attempt));
            return;
        }
        fundSyncRunMapper.updateById(run);
    }

    private void sleepBackoff(int attempt, Integer retryAfterSeconds) {
        long baseMillis = Math.max(100L, properties.getRetryBaseBackoff().toMillis());
        long maxMillis = Math.max(baseMillis, properties.getRetryMaxBackoff().toMillis());
        long suggestedMillis = retryAfterSeconds == null ? 0L : TimeUnit.SECONDS.toMillis(retryAfterSeconds);
        long delay = Math.min(maxMillis, Math.max(suggestedMillis, baseMillis * (1L << Math.min(attempt - 1, 6))));
        try {
            TimeUnit.MILLISECONDS.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("基金数据同步退避等待被中断");
        }
    }

    private String normalizeFundCode(String fundCode) {
        String normalized = fundCode == null ? "" : fundCode.trim();
        if (!isFundCode(normalized)) {
            throw new ServiceException("基金代码格式不正确");
        }
        return normalized;
    }

    private void ensureSyncEnabled() {
        if (!properties.isEnabled()) {
            throw new ServiceException("基金数据同步已通过部署配置关闭");
        }
    }

    private String normalizeDataset(String dataset) {
        if (dataset == null || dataset.isBlank()) {
            return dataset;
        }
        return dataset.trim().toUpperCase(Locale.ROOT);
    }

    private int normalizeDays(int days) {
        if (days < 0 || days > 5000) {
            throw new ServiceException("净值查询天数必须在 0 到 5000 之间");
        }
        return days;
    }

    private boolean isFundCode(String fundCode) {
        return fundCode != null && fundCode.matches("^\\d{6}$");
    }

    private int parsePage(String cursorValue) {
        if (cursorValue == null || cursorValue.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(cursorValue));
        } catch (NumberFormatException e) {
            throw new ServiceException("全量同步游标必须是页码");
        }
    }

    private LocalDate parseReportDate(FundHoldingProviderResponse record) {
        if (record.getReportDate() != null) {
            return record.getReportDate();
        }
        String period = record.getReportPeriod();
        if (period == null || period.isBlank()) {
            return null;
        }
        return LocalDate.parse(period.length() == 7 ? period + "-01" : period.substring(0, 10));
    }

    private String newBatchId(String prefix) {
        return prefix.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_")
            + "_" + OffsetDateTime.now().format(BATCH_TIME) + "_" + IdGeneratorUtil.nextLongId();
    }

    private String versionFromBatch(FundSyncBatchMetaDto meta) {
        return meta == null || meta.getBatchId() == null ? null : "v-" + meta.getBatchId();
    }

    private String stableChecksum(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("当前 JVM 不支持 SHA-256");
        }
    }

    private ServiceException toServiceException(String message, Exception e) {
        if (e instanceof ServiceException serviceException) {
            return serviceException;
        }
        if (e instanceof FundProviderException providerException) {
            return new ServiceException(providerException.getMessage())
                .setDetailMessage("provider:" + providerException.getErrorCode());
        }
        return new ServiceException(message).setDetailMessage(e.getMessage());
    }

    private String errorCode(Exception e) {
        if (e instanceof FundProviderException providerException) {
            return providerException.getErrorCode();
        }
        if (e instanceof ServiceException serviceException
            && serviceException.getDetailMessage() != null
            && serviceException.getDetailMessage().startsWith("provider:")) {
            return serviceException.getDetailMessage().substring("provider:".length());
        }
        return e instanceof ServiceException ? "SERVICE_ERROR" : e.getClass().getSimpleName();
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private String nullToAll(String value) {
        return value == null || value.isBlank() ? "ALL" : value;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String blankToDefault(String value, String firstDefault, String secondDefault) {
        return value == null || value.isBlank() ? blankToDefault(firstDefault, secondDefault) : value;
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private org.dromara.fund.domain.vo.FundSyncRunVo requireRunVo(Long id) {
        org.dromara.fund.domain.vo.FundSyncRunVo run = fundSyncRunMapper.selectRunDetail(id);
        if (run == null) {
            throw new ServiceException("同步运行记录不存在");
        }
        return run;
    }

    private record PersistResult(String state, int changedCount, int rejectedCount) {
    }
}
