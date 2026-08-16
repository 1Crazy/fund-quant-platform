-- 历史 NAV 位置结果持久化（PostgreSQL 17）
-- 执行顺序：在 update_quant_config_center_v1.sql 之后执行。
-- 结果按基金和量化发布版本隔离；全量计算完成后可由列表和区域筛选稳定读取。

BEGIN;

CREATE TABLE IF NOT EXISTS fund_nav_position (
    id                          bigint          PRIMARY KEY,
    fund_code                   varchar(12)     NOT NULL,
    trade_date                  date,
    calculated_at               timestamptz     NOT NULL,
    status                      varchar(32)     NOT NULL,
    algorithm_version           varchar(64)     NOT NULL,
    config_release_version      bigint          NOT NULL,
    config_release_checksum     char(64)        NOT NULL,
    nav_position_config_version bigint,
    nav_position_config_checksum char(64),
    input_data_version          varchar(128),
    nav_percentile              numeric(12, 6),
    current_drawdown            numeric(12, 6),
    ma60_deviation              numeric(12, 6),
    ma120_deviation             numeric(12, 6),
    ma250_deviation             numeric(12, 6),
    nav_position_score          numeric(12, 6),
    nav_position_region         varchar(32),
    sample_count                integer,
    effective_start_date        date,
    effective_end_date          date,
    reasons                     jsonb           NOT NULL DEFAULT '[]'::jsonb,
    indicators                  jsonb           NOT NULL DEFAULT '[]'::jsonb,
    create_dept                 bigint,
    create_by                   bigint,
    create_time                 timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by                   bigint,
    update_time                 timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_nav_position_release
        UNIQUE (fund_code, config_release_version, config_release_checksum),
    CONSTRAINT fk_fund_nav_position_code
        FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT fk_fund_nav_position_config_release
        FOREIGN KEY (config_release_version) REFERENCES quant_config_release (release_version),
    CONSTRAINT ck_fund_nav_position_release_checksum
        CHECK (config_release_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_fund_nav_position_group_checksum
        CHECK (nav_position_config_checksum IS NULL OR nav_position_config_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_fund_nav_position_sample_count
        CHECK (sample_count IS NULL OR sample_count >= 0),
    CONSTRAINT ck_fund_nav_position_reasons
        CHECK (jsonb_typeof(reasons) = 'array'),
    CONSTRAINT ck_fund_nav_position_indicators
        CHECK (jsonb_typeof(indicators) = 'array')
);

COMMENT ON TABLE fund_nav_position IS '基金历史 NAV 位置结果（跨租户共享，按量化发布版本持久化）';
COMMENT ON COLUMN fund_nav_position.nav_position_region IS '历史位置区域：LOW_VALUATION、NORMAL、HIGH_VALUATION、RISK；无有效结果时为空';

CREATE INDEX IF NOT EXISTS idx_fund_nav_position_release_region
    ON fund_nav_position (config_release_version, config_release_checksum, nav_position_region, fund_code);
CREATE INDEX IF NOT EXISTS idx_fund_nav_position_release_calculated_at
    ON fund_nav_position (config_release_version, config_release_checksum, calculated_at DESC);

COMMIT;
