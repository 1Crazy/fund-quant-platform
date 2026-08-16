-- Stage3 收盘快照与闭市缓存运维参数。
-- 执行顺序：在 update_harden_realtime_fund_estimation_v1.sql 后执行。
-- 回滚/兼容：将 schedule.enabled 设为 false 或 snapshot.close-time 设为 OFF 即可停止新行为；保留已有快照和配置行无害。

BEGIN;

DO $$
DECLARE
    item record;
    next_config_id bigint;
BEGIN
    FOR item IN
        SELECT * FROM (VALUES
            ('基金估值闭市 Redis TTL（秒）', 'fund.estimate.cache.closed-ttl-seconds', '1800', '非交易时段保留最后成功估值的缓存时长'),
            ('基金估值收盘快照时间', 'fund.estimate.snapshot.close-time', '15:00', '交易日 HH:mm；设为 OFF 时不额外强制收盘快照')
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

COMMIT;
