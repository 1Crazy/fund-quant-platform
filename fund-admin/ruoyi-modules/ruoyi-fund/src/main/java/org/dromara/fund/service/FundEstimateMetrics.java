package org.dromara.fund.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.dromara.fund.domain.vo.FundEstimateVo;
import org.springframework.stereotype.Component;

/**
 * 估值链路的低基数指标。
 *
 * <p>标签只能表达结果类别，不能包含基金代码、发布校验和或异常原文，避免度量基数失控。</p>
 */
@Component
@RequiredArgsConstructor
public class FundEstimateMetrics {

    private final MeterRegistry meterRegistry;

    public Timer.Sample startProviderRequest() {
        return Timer.start(meterRegistry);
    }

    public Timer.Sample startScheduleRun() {
        return Timer.start(meterRegistry);
    }

    public void recordProviderRequest(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("fund.estimate.provider.duration")
            .tag("outcome", outcome)
            .register(meterRegistry));
    }

    public void recordScheduleRun(Timer.Sample sample) {
        sample.stop(Timer.builder("fund.estimate.schedule.duration").register(meterRegistry));
    }

    public void recordCache(boolean hit) {
        Counter.builder("fund.estimate.cache.requests")
            .tag("result", hit ? "hit" : "miss")
            .register(meterRegistry)
            .increment();
    }

    public void recordResult(FundEstimateVo estimate) {
        String sourceStatus = estimate.getSourceStatus() == null ? "UNKNOWN" : estimate.getSourceStatus();
        Counter.builder("fund.estimate.results")
            .tag("source_status", sourceStatus)
            .register(meterRegistry)
            .increment();
        recordCoverage("holding", estimate.getHoldingCoverageRate());
        recordCoverage("quote", estimate.getQuoteCoverageRate());
    }

    public void recordStaleFallback(boolean fallbackAvailable) {
        Counter.builder("fund.estimate.stale.fallbacks")
            .tag("result", fallbackAvailable ? "stale_snapshot" : "upstream_failed")
            .register(meterRegistry)
            .increment();
    }

    public void recordSnapshot(boolean persisted) {
        Counter.builder("fund.estimate.snapshots")
            .tag("result", persisted ? "persisted" : "throttled")
            .register(meterRegistry)
            .increment();
    }

    public void recordCleanup(int deleted) {
        if (deleted <= 0) {
            return;
        }
        Counter.builder("fund.estimate.retention.deleted")
            .register(meterRegistry)
            .increment(deleted);
    }

    private void recordCoverage(String kind, java.math.BigDecimal coverage) {
        if (coverage == null) {
            return;
        }
        DistributionSummary.builder("fund.estimate.coverage.percent")
            .tag("kind", kind)
            .register(meterRegistry)
            .record(coverage.doubleValue());
    }
}
