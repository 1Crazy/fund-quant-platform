-- Stage3 精度契约升级草稿：不修改 cross-market-fund-v1，也不直接创建发布记录。
-- 前置：已执行 update_quant_config_center_v1.sql，且 Java/Python 已支持 ESTIMATE schema v2。
-- 发布时应创建包含本草稿与其余九个既有已校验组的新发布版本。

BEGIN;

INSERT INTO quant_config_version (
    id, config_code, config_version, schema_version, status, config_json, checksum,
    effective_from, revision, create_dept, create_by, create_time, update_time, remark
) VALUES (
    1702011, 'ESTIMATE', 2, 2, 'DRAFT',
    '{"max_quote_age_seconds":90,"min_holding_coverage_percent":60,"nav_decimal_scale":6,"percentage_decimal_scale":4}'::jsonb,
    'f2a734ffc5923d067fd73441c90b9f2a4743ab756d33ba6c912d212c8cb066af',
    '2026-08-10T09:30:00+08:00', 0, 103, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
    'Stage3：NAV 保留 6 位小数；涨跌幅、权重、贡献和覆盖率保留 4 位小数'
)
ON CONFLICT (config_code, config_version) DO NOTHING;

COMMIT;
