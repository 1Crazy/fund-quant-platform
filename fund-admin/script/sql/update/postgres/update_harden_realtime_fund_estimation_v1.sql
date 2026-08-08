-- Stage3 harden-realtime-fund-estimation 前向迁移。
-- 执行顺序：在 update_fund_data_center_v1.sql 和 update_quant_config_center_v1.sql 后执行。
-- 回滚/兼容：关闭 Stage3 调度与新 Redis key 后可保留这些只读快照列；旧客户端继续读取既有字段。

BEGIN;

ALTER TABLE fund_estimate
    ADD COLUMN IF NOT EXISTS status_reason varchar(256),
    ADD COLUMN IF NOT EXISTS holding_coverage_rate numeric(8, 4),
    ADD COLUMN IF NOT EXISTS quote_coverage_rate numeric(8, 4),
    ADD COLUMN IF NOT EXISTS missing_quote_count integer,
    ADD COLUMN IF NOT EXISTS quote_time timestamptz,
    ADD COLUMN IF NOT EXISTS holding_report_date date,
    ADD COLUMN IF NOT EXISTS holding_report_period varchar(64),
    ADD COLUMN IF NOT EXISTS input_data_version varchar(128),
    ADD COLUMN IF NOT EXISTS algorithm_version varchar(64),
    ADD COLUMN IF NOT EXISTS trade_date date,
    ADD COLUMN IF NOT EXISTS estimate_config_version bigint,
    ADD COLUMN IF NOT EXISTS estimate_config_checksum char(64);

-- Python 只使用独立只读角色读取 Stage1 共享输入；不授予 fund_estimate 或租户私有表写权限。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fund_quant_reader') THEN
        GRANT SELECT ON fund_info, fund_nav, fund_holding TO fund_quant_reader;
    END IF;
END $$;

-- 刷新频率、缓存、锁和批大小都是运维参数，不能混入量化配置发布。
DO $$
DECLARE
    item record;
    next_config_id bigint;
BEGIN
    FOR item IN
        SELECT * FROM (VALUES
            ('基金估值上游连接超时（毫秒）', 'fund.estimate.provider.connect-timeout-ms', '2000', 'Java 到 fund-quant 的连接超时'),
            ('基金估值上游读取超时（毫秒）', 'fund.estimate.provider.read-timeout-ms', '30000', 'Java 到 fund-quant 的读取超时'),
            ('基金估值 Redis TTL（秒）', 'fund.estimate.cache.ttl-seconds', '45', '正常估值短缓存'),
            ('基金估值供应方结果缓存（秒）', 'fund.estimate.provider-result-cache-seconds', '15', 'fund-quant 进程内结果 Redis 缓存窗口'),
            ('基金估值成分行情缓存（秒）', 'fund.estimate.market-quote-cache-seconds', '15', 'fund-quant 批量成分行情缓存窗口'),
            ('基金估值陈旧判定（秒）', 'fund.estimate.stale-after-seconds', '180', '超过此时长的快照只能作为 STALE 降级结果'),
            ('基金估值锁等待（毫秒）', 'fund.estimate.lock.wait-millis', '800', '单基金请求合并等待上限'),
            ('基金估值锁租约（毫秒）', 'fund.estimate.lock.lease-millis', '5000', '单基金估值锁租约'),
            ('基金估值快照节流（秒）', 'fund.estimate.snapshot.throttle-seconds', '300', '同一基金同一发布版本的常规快照最小间隔'),
            ('基金估值调度开关', 'fund.estimate.schedule.enabled', 'false', '上线前保持 false，由运维显式启用'),
            ('基金估值调度 cron', 'fund.estimate.schedule.cron', '*/30 * * * * MON-FRI', '每 30 秒触发一次，服务内再执行交易时段门禁'),
            ('基金估值调度时区', 'fund.estimate.schedule.zone-id', 'Asia/Shanghai', '调度触发时区，不定义估值结果时间语义'),
            ('基金估值调度锁租约（秒）', 'fund.estimate.schedule.lock-lease-seconds', '240', '批量刷新分布式锁租约'),
            ('基金估值热点批大小', 'fund.estimate.schedule.batch-size', '50', '每个批次最多刷新基金数'),
            ('基金估值热点基金代码', 'fund.estimate.schedule.hot-fund-codes', '', '逗号分隔六位基金代码；组合范围由后续 Stage7 扩展'),
            ('基金估值交易时段', 'fund.estimate.schedule.trading-sessions', '09:30-11:30,13:00-15:00', '调度使用的本地交易时段；结果语义时区仍由量化发布决定'),
            ('基金估值非交易日', 'fund.estimate.schedule.holidays', '', '逗号分隔 yyyy-MM-dd 非交易日；应由数据中心交易日历年度维护'),
            ('基金估值快照保留天数', 'fund.estimate.retention-days', '180', 'SnailJob 清理非审计快照的保留期')
        ) AS seed(config_name, config_key, config_value, remark)
    LOOP
        IF NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = item.config_key) THEN
            SELECT COALESCE(MAX(config_id), 0) + 1 INTO next_config_id FROM sys_config;
            INSERT INTO sys_config (
                config_id, tenant_id, config_name, config_key, config_value, config_type,
                create_dept, create_by, create_time, remark
            ) VALUES (
                next_config_id, '000000', item.config_name, item.config_key, item.config_value, 'N',
                103, 1, CURRENT_TIMESTAMP, item.remark
            );
        END IF;
    END LOOP;
END $$;

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES
    (17016, '基金估值刷新', 17001, 2, '#', '', NULL, '1', '0', 'F', '0', '0',
     'fund:estimate:refresh', '#', 103, 1, CURRENT_TIMESTAMP, NULL, NULL,
     '手动触发盘中估值；仅返回公开披露持仓的数学估算'),
    (17017, '基金估值监控', 17001, 3, '#', '', NULL, '1', '0', 'F', '0', '0',
     'fund:estimate:monitor', '#', 103, 1, CURRENT_TIMESTAMP, NULL, NULL,
     '查看估值调度批次、锁和当前交易时段状态')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    perms = EXCLUDED.perms,
    remark = EXCLUDED.remark,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 17016), (1, 17017)
ON CONFLICT (role_id, menu_id) DO NOTHING;

ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_source_status;
ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_status;
ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_holding_coverage;
ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_quote_coverage;
ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_missing_quote_count;
ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_group_checksum;
ALTER TABLE fund_estimate DROP CONSTRAINT IF EXISTS ck_fund_estimate_config_lineage;
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_source_status
        CHECK (source_status IN ('NORMAL', 'PARTIAL', 'UNSUPPORTED', 'STALE', 'FAILED', 'UPSTREAM_FAILED'));
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_holding_coverage
        CHECK (holding_coverage_rate IS NULL OR holding_coverage_rate BETWEEN 0 AND 100);
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_quote_coverage
        CHECK (quote_coverage_rate IS NULL OR quote_coverage_rate BETWEEN 0 AND 100);
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_missing_quote_count
        CHECK (missing_quote_count IS NULL OR missing_quote_count >= 0);
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_group_checksum
        CHECK (estimate_config_checksum IS NULL OR estimate_config_checksum ~ '^[0-9a-f]{64}$');
-- Stage2 已写入的历史快照没有发布血缘；允许二者同时为空，但禁止只写一半血缘。
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_config_lineage
        CHECK (
            (config_release_version IS NULL AND config_release_checksum IS NULL)
            OR (config_release_version IS NOT NULL AND config_release_checksum ~ '^[0-9a-f]{64}$')
        ) NOT VALID;

-- 历史行没有行情覆盖率、报价时间和配置组血缘，必须以陈旧兼容载荷读取。
UPDATE fund_estimate
SET source_status = 'STALE',
    status_reason = COALESCE(status_reason, 'LEGACY_SNAPSHOT_METADATA_UNAVAILABLE')
WHERE holding_coverage_rate IS NULL
   OR quote_coverage_rate IS NULL
   OR algorithm_version IS NULL
   OR estimate_config_version IS NULL
   OR estimate_config_checksum IS NULL;

CREATE INDEX IF NOT EXISTS idx_fund_estimate_release_trade_date
    ON fund_estimate (config_release_version, config_release_checksum, trade_date DESC, fund_code);
CREATE INDEX IF NOT EXISTS idx_fund_estimate_retention
    ON fund_estimate (estimate_time ASC, fund_code);

COMMENT ON COLUMN fund_estimate.holding_coverage_rate IS '公开披露股票持仓覆盖率，百分数口径';
COMMENT ON COLUMN fund_estimate.quote_coverage_rate IS '有可接受实时行情的披露股票权重，百分数口径';
COMMENT ON COLUMN fund_estimate.status_reason IS 'NORMAL 以外状态的稳定原因码或受控摘要';
COMMENT ON COLUMN fund_estimate.estimate_config_version IS '估值计算固定使用的 ESTIMATE 配置组版本';
COMMENT ON COLUMN fund_estimate.estimate_config_checksum IS '估值计算固定使用的 ESTIMATE 配置组校验和';

COMMIT;
