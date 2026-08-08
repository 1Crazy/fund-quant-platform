package org.dromara.fund.config;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.service.ConfigService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.support.CronExpression;

/**
 * 估值运行参数的 sys_config 访问层。
 *
 * <p>这里只承载超时、缓存、调度和批处理等运维值；任何会改变估值结果语义的阈值、精度和时区
 * 必须继续由已锁定的量化配置发布提供。</p>
 */
@Component
public class FundEstimateRuntimeSettings {

    public static final String PROVIDER_CONNECT_TIMEOUT_MS = "fund.estimate.provider.connect-timeout-ms";
    public static final String PROVIDER_READ_TIMEOUT_MS = "fund.estimate.provider.read-timeout-ms";
    public static final String CACHE_TTL_SECONDS = "fund.estimate.cache.ttl-seconds";
    public static final String PROVIDER_RESULT_CACHE_SECONDS = "fund.estimate.provider-result-cache-seconds";
    public static final String MARKET_QUOTE_CACHE_SECONDS = "fund.estimate.market-quote-cache-seconds";
    public static final String STALE_AFTER_SECONDS = "fund.estimate.stale-after-seconds";
    public static final String LOCK_WAIT_MILLIS = "fund.estimate.lock.wait-millis";
    public static final String LOCK_LEASE_MILLIS = "fund.estimate.lock.lease-millis";
    public static final String SNAPSHOT_THROTTLE_SECONDS = "fund.estimate.snapshot.throttle-seconds";
    public static final String SCHEDULE_ENABLED = "fund.estimate.schedule.enabled";
    public static final String SCHEDULE_CRON = "fund.estimate.schedule.cron";
    public static final String SCHEDULE_ZONE_ID = "fund.estimate.schedule.zone-id";
    public static final String SCHEDULE_LOCK_LEASE_SECONDS = "fund.estimate.schedule.lock-lease-seconds";
    public static final String SCHEDULE_BATCH_SIZE = "fund.estimate.schedule.batch-size";
    public static final String HOT_FUND_CODES = "fund.estimate.schedule.hot-fund-codes";
    public static final String SCHEDULE_TRADING_SESSIONS = "fund.estimate.schedule.trading-sessions";
    public static final String SCHEDULE_HOLIDAYS = "fund.estimate.schedule.holidays";
    public static final String RETENTION_DAYS = "fund.estimate.retention-days";

    private final ConfigService configService;

    public FundEstimateRuntimeSettings(ConfigService configService) {
        this.configService = configService;
    }

    public Duration getProviderConnectTimeout() {
        return Duration.ofMillis(getPositiveLong(PROVIDER_CONNECT_TIMEOUT_MS));
    }

    public Duration getProviderReadTimeout() {
        return Duration.ofMillis(getPositiveLong(PROVIDER_READ_TIMEOUT_MS));
    }

    public Duration getCacheTtl() {
        return Duration.ofSeconds(getPositiveLong(CACHE_TTL_SECONDS));
    }

    public int getProviderResultCacheSeconds() {
        return getPositiveInt(PROVIDER_RESULT_CACHE_SECONDS, 300);
    }

    public int getMarketQuoteCacheSeconds() {
        return getPositiveInt(MARKET_QUOTE_CACHE_SECONDS, 300);
    }

    public Duration getStaleAfter() {
        return Duration.ofSeconds(getPositiveLong(STALE_AFTER_SECONDS));
    }

    public long getLockWaitMillis() {
        return getPositiveLong(LOCK_WAIT_MILLIS);
    }

    public long getLockLeaseMillis() {
        return getPositiveLong(LOCK_LEASE_MILLIS);
    }

    public long getSnapshotThrottleSeconds() {
        return getPositiveLong(SNAPSHOT_THROTTLE_SECONDS);
    }

    public boolean isScheduleEnabled() {
        String value = required(SCHEDULE_ENABLED);
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw invalid(SCHEDULE_ENABLED, "必须为 true 或 false");
    }

    public String getScheduleCron() {
        String cron = required(SCHEDULE_CRON);
        try {
            CronExpression.parse(cron);
            return cron;
        } catch (IllegalArgumentException error) {
            throw invalid(SCHEDULE_CRON, "不是有效的 Spring cron 表达式");
        }
    }

    public ZoneId getScheduleZoneId() {
        try {
            return ZoneId.of(required(SCHEDULE_ZONE_ID));
        } catch (RuntimeException error) {
            throw invalid(SCHEDULE_ZONE_ID, "不是有效时区");
        }
    }

    public Duration getScheduleLockLease() {
        return Duration.ofSeconds(getPositiveLong(SCHEDULE_LOCK_LEASE_SECONDS));
    }

    public int getScheduleBatchSize() {
        return getPositiveInt(SCHEDULE_BATCH_SIZE, 1000);
    }

    public List<String> getHotFundCodes() {
        String value = configService.getConfigValue(HOT_FUND_CODES);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> codes = Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(code -> !code.isBlank())
            .toList();
        if (codes.stream().anyMatch(code -> !code.matches("^\\d{6}$"))) {
            throw invalid(HOT_FUND_CODES, "必须是以逗号分隔的六位基金代码");
        }
        return codes;
    }

    public boolean isActiveTradingSession(ZonedDateTime currentTime) {
        LocalDate date = currentTime.toLocalDate();
        if (currentTime.getDayOfWeek() == DayOfWeek.SATURDAY || currentTime.getDayOfWeek() == DayOfWeek.SUNDAY
            || getScheduleHolidays().contains(date)) {
            return false;
        }
        LocalTime now = currentTime.toLocalTime();
        return Arrays.stream(required(SCHEDULE_TRADING_SESSIONS).split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(this::parseTradingSession)
            .anyMatch(session -> !now.isBefore(session.start()) && now.isBefore(session.end()));
    }

    private TradingSession parseTradingSession(String value) {
        String[] boundaries = value.split("-", -1);
        if (boundaries.length != 2) {
            throw invalid(SCHEDULE_TRADING_SESSIONS, "必须是 HH:mm-HH:mm 的逗号分隔区间");
        }
        try {
            LocalTime start = LocalTime.parse(boundaries[0].trim());
            LocalTime end = LocalTime.parse(boundaries[1].trim());
            if (!start.isBefore(end)) {
                throw invalid(SCHEDULE_TRADING_SESSIONS, "区间起点必须早于终点");
            }
            return new TradingSession(start, end);
        } catch (java.time.format.DateTimeParseException error) {
            throw invalid(SCHEDULE_TRADING_SESSIONS, "必须是 HH:mm-HH:mm 的逗号分隔区间");
        }
    }

    private Set<LocalDate> getScheduleHolidays() {
        String value = configService.getConfigValue(SCHEDULE_HOLIDAYS);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        try {
            return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(date -> !date.isBlank())
                .map(LocalDate::parse)
                .collect(Collectors.toUnmodifiableSet());
        } catch (java.time.format.DateTimeParseException error) {
            throw invalid(SCHEDULE_HOLIDAYS, "必须是 yyyy-MM-dd 的逗号分隔日期");
        }
    }

    public int getRetentionDays() {
        return getPositiveInt(RETENTION_DAYS, 3650);
    }

    private int getPositiveInt(String key, int maximum) {
        long value = getPositiveLong(key);
        if (value > maximum) {
            throw invalid(key, "必须小于或等于 " + maximum);
        }
        return (int) value;
    }

    private long getPositiveLong(String key) {
        try {
            long value = Long.parseLong(required(key));
            if (value <= 0) {
                throw invalid(key, "必须为正整数");
            }
            return value;
        } catch (NumberFormatException error) {
            throw invalid(key, "必须为正整数");
        }
    }

    private String required(String key) {
        String value = configService.getConfigValue(key);
        if (value == null || value.isBlank()) {
            throw new ServiceException("缺少基金估值运行参数: " + key);
        }
        return value.trim();
    }

    private ServiceException invalid(String key, String reason) {
        return new ServiceException("基金估值运行参数无效 " + key + ": " + reason);
    }

    private record TradingSession(LocalTime start, LocalTime end) {
    }
}
