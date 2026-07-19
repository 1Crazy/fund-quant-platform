-- 基金量化决策系统 V1（PostgreSQL）
-- 约定：行情与计算结果是全租户共享数据；组合及组合风险属于租户私有数据。
-- Java 实体使用 RuoYi-Vue-Plus 的 BaseEntity/TenantEntity，主键由 MyBatis-Plus 雪花算法生成。

BEGIN;

CREATE TABLE fund_info (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    fund_name       varchar(160)    NOT NULL,
    fund_type       varchar(32)     NOT NULL,
    pinyin_abbr     varchar(64),
    manager_name    varchar(160),
    custodian_name  varchar(160),
    establish_date  date,
    benchmark       varchar(500),
    risk_level      varchar(16),
    fund_scale      numeric(20, 4),
    status          char(1)         NOT NULL DEFAULT '0',
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_updated_at timestamptz,
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag        bigint          NOT NULL DEFAULT 0,
    CONSTRAINT uk_fund_info_code UNIQUE (fund_code),
    CONSTRAINT ck_fund_info_status CHECK (status IN ('0', '1')),
    CONSTRAINT ck_fund_info_del_flag CHECK (del_flag IN (0, 1))
);

COMMENT ON TABLE fund_info IS '基金基础信息（跨租户共享）';
COMMENT ON COLUMN fund_info.status IS '状态：0正常，1停用';
COMMENT ON COLUMN fund_info.fund_scale IS '基金规模，单位亿元';

CREATE INDEX idx_fund_info_name ON fund_info (fund_name);
CREATE INDEX idx_fund_info_type_status ON fund_info (fund_type, status) WHERE del_flag = 0;

CREATE TABLE fund_nav (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    nav_date        date            NOT NULL,
    unit_nav        numeric(18, 6)  NOT NULL,
    accumulated_nav numeric(18, 6),
    daily_growth_rate numeric(12, 6),
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_nav_code_date UNIQUE (fund_code, nav_date),
    CONSTRAINT fk_fund_nav_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code)
);

COMMENT ON TABLE fund_nav IS '基金历史净值（跨租户共享）';
CREATE INDEX idx_fund_nav_code_date_desc ON fund_nav (fund_code, nav_date DESC);

CREATE TABLE fund_estimate (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    estimate_time   timestamptz     NOT NULL,
    estimate_nav    numeric(18, 6)  NOT NULL,
    estimate_growth_rate numeric(12, 6),
    previous_nav    numeric(18, 6),
    previous_nav_date date,
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_status   varchar(16)     NOT NULL DEFAULT 'NORMAL',
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_estimate_code_time UNIQUE (fund_code, estimate_time),
    CONSTRAINT fk_fund_estimate_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_estimate_source_status CHECK (source_status IN ('NORMAL', 'STALE', 'FAILED'))
);

COMMENT ON TABLE fund_estimate IS '基金盘中估值快照（跨租户共享）';
CREATE INDEX idx_fund_estimate_code_time_desc ON fund_estimate (fund_code, estimate_time DESC);

CREATE TABLE fund_trend_snapshot (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    trade_date      date            NOT NULL,
    window_days     smallint        NOT NULL DEFAULT 120,
    sample_count    integer         NOT NULL,
    score           smallint        NOT NULL,
    trend           varchar(16)     NOT NULL,
    ma5             numeric(18, 6),
    ma10            numeric(18, 6),
    ma20            numeric(18, 6),
    ma60            numeric(18, 6),
    ma120           numeric(18, 6),
    rsi             numeric(12, 6),
    macd_dif        numeric(18, 8),
    macd_dea        numeric(18, 8),
    macd_hist       numeric(18, 8),
    max_drawdown    numeric(12, 6),
    volatility      numeric(12, 6),
    algorithm_version varchar(32)   NOT NULL,
    calculated_at   timestamptz     NOT NULL,
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_trend_code_date_ver UNIQUE (fund_code, trade_date, algorithm_version),
    CONSTRAINT fk_fund_trend_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_trend_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_fund_trend_type CHECK (trend IN ('bull', 'neutral', 'bear'))
);

COMMENT ON TABLE fund_trend_snapshot IS '120日趋势指标快照（跨租户共享，可按算法版本重算）';
CREATE INDEX idx_fund_trend_code_date_desc ON fund_trend_snapshot (fund_code, trade_date DESC);

CREATE TABLE fund_signal_snapshot (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    trade_date      date            NOT NULL,
    signal          varchar(8)      NOT NULL,
    score           smallint        NOT NULL,
    reasons         jsonb           NOT NULL DEFAULT '[]'::jsonb,
    rule_details    jsonb           NOT NULL DEFAULT '{}'::jsonb,
    algorithm_version varchar(32)   NOT NULL,
    calculated_at   timestamptz     NOT NULL,
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_signal_code_date_ver UNIQUE (fund_code, trade_date, algorithm_version),
    CONSTRAINT fk_fund_signal_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_signal_type CHECK (signal IN ('BUY', 'SELL', 'HOLD')),
    CONSTRAINT ck_fund_signal_score CHECK (score BETWEEN 0 AND 100)
);

COMMENT ON TABLE fund_signal_snapshot IS '基金买卖信号快照，reasons 保存可解释原因';
CREATE INDEX idx_fund_signal_code_date_desc ON fund_signal_snapshot (fund_code, trade_date DESC);

CREATE TABLE market_temperature (
    id              bigint          PRIMARY KEY,
    trade_date      date            NOT NULL,
    score           smallint        NOT NULL,
    level           varchar(16)     NOT NULL,
    algorithm_version varchar(32)   NOT NULL,
    calculated_at   timestamptz     NOT NULL,
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_market_temperature_date_ver UNIQUE (trade_date, algorithm_version),
    CONSTRAINT ck_market_temperature_score CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT ck_market_temperature_level CHECK (level IN ('极度低估', '低估', '合理', '高估', '极度高估'))
);

CREATE TABLE market_temperature_item (
    id              bigint          PRIMARY KEY,
    temperature_id  bigint          NOT NULL,
    indicator_code  varchar(32)     NOT NULL,
    indicator_name  varchar(64)     NOT NULL,
    raw_value       numeric(20, 8),
    percentile      numeric(12, 6),
    normalized_score numeric(12, 6) NOT NULL,
    weight          numeric(8, 6)   NOT NULL,
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_time     timestamptz,
    is_stale        boolean         NOT NULL DEFAULT false,
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_market_temperature_item UNIQUE (temperature_id, indicator_code),
    CONSTRAINT fk_market_temperature_item FOREIGN KEY (temperature_id) REFERENCES market_temperature (id) ON DELETE CASCADE,
    CONSTRAINT ck_market_temperature_item_score CHECK (normalized_score BETWEEN 0 AND 100),
    CONSTRAINT ck_market_temperature_item_weight CHECK (weight > 0 AND weight <= 1)
);

COMMENT ON TABLE market_temperature_item IS '市场温度分项：沪深300PB、中证500PB、创业板PB、北向资金、股债收益差';

CREATE TABLE fund_holding (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    report_date     date            NOT NULL,
    security_code   varchar(24)     NOT NULL,
    security_name   varchar(160)    NOT NULL,
    holding_ratio   numeric(12, 6)  NOT NULL,
    market_value    numeric(20, 4),
    rank_no         integer,
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_holding_report_security UNIQUE (fund_code, report_date, security_code),
    CONSTRAINT fk_fund_holding_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_holding_ratio CHECK (holding_ratio BETWEEN 0 AND 100)
);

CREATE INDEX idx_fund_holding_code_report ON fund_holding (fund_code, report_date DESC);

CREATE TABLE fund_industry_allocation (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    report_date     date            NOT NULL,
    industry_code   varchar(32)     NOT NULL,
    industry_name   varchar(100)    NOT NULL,
    allocation_rate numeric(12, 6)  NOT NULL,
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_industry_report UNIQUE (fund_code, report_date, industry_code),
    CONSTRAINT fk_fund_industry_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_industry_rate CHECK (allocation_rate BETWEEN 0 AND 100)
);

CREATE INDEX idx_fund_industry_code_report ON fund_industry_allocation (fund_code, report_date DESC);

CREATE TABLE fund_portfolio (
    id              bigint          PRIMARY KEY,
    tenant_id       varchar(20)     NOT NULL DEFAULT '000000',
    portfolio_name  varchar(100)    NOT NULL,
    description     varchar(500),
    base_amount     numeric(20, 4),
    status          char(1)         NOT NULL DEFAULT '0',
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag        bigint          NOT NULL DEFAULT 0,
    CONSTRAINT ck_fund_portfolio_status CHECK (status IN ('0', '1')),
    CONSTRAINT ck_fund_portfolio_del_flag CHECK (del_flag IN (0, 1))
);

COMMENT ON TABLE fund_portfolio IS '租户私有基金组合';
CREATE INDEX idx_fund_portfolio_tenant_user ON fund_portfolio (tenant_id, create_by) WHERE del_flag = 0;

CREATE TABLE fund_portfolio_item (
    id              bigint          PRIMARY KEY,
    tenant_id       varchar(20)     NOT NULL DEFAULT '000000',
    portfolio_id    bigint          NOT NULL,
    fund_code       varchar(12)     NOT NULL,
    weight          numeric(12, 6)  NOT NULL,
    amount          numeric(20, 4),
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_portfolio_item UNIQUE (portfolio_id, fund_code),
    CONSTRAINT fk_fund_portfolio_item_portfolio FOREIGN KEY (portfolio_id) REFERENCES fund_portfolio (id) ON DELETE CASCADE,
    CONSTRAINT fk_fund_portfolio_item_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_portfolio_item_weight CHECK (weight > 0 AND weight <= 100)
);

CREATE TABLE portfolio_risk_snapshot (
    id              bigint          PRIMARY KEY,
    tenant_id       varchar(20)     NOT NULL DEFAULT '000000',
    portfolio_id    bigint          NOT NULL,
    trade_date      date            NOT NULL,
    risk_level      varchar(8)      NOT NULL,
    risk_score      smallint        NOT NULL,
    overlap_rate    numeric(12, 6)  NOT NULL,
    industry_rate   numeric(12, 6)  NOT NULL,
    volatility      numeric(12, 6)  NOT NULL,
    max_drawdown    numeric(12, 6)  NOT NULL,
    details         jsonb           NOT NULL DEFAULT '{}'::jsonb,
    algorithm_version varchar(32)   NOT NULL,
    calculated_at   timestamptz     NOT NULL,
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_portfolio_risk_date_ver UNIQUE (portfolio_id, trade_date, algorithm_version),
    CONSTRAINT fk_portfolio_risk_portfolio FOREIGN KEY (portfolio_id) REFERENCES fund_portfolio (id) ON DELETE CASCADE,
    CONSTRAINT ck_portfolio_risk_score CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT ck_portfolio_risk_overlap CHECK (overlap_rate BETWEEN 0 AND 100),
    CONSTRAINT ck_portfolio_risk_industry CHECK (industry_rate BETWEEN 0 AND 100)
);

CREATE INDEX idx_portfolio_risk_portfolio_date ON portfolio_risk_snapshot (tenant_id, portfolio_id, trade_date DESC);

-- ----------------------------
-- 基金实时估值菜单与权限
-- 菜单 ID 使用独立号段，重复执行时通过 ON CONFLICT 保持幂等。
-- ----------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES
    (1600, '基金中心', 0, 10, 'fund', NULL, NULL, '1', '0', 'M', '0', '0', '', 'chart-no-axes-combined', 103, 1, now(), NULL, NULL, '基金量化决策业务菜单'),
    (1601, '基金实时估值', 1600, 1, 'list', 'fund/list/index', NULL, '1', '0', 'C', '0', '0', 'fund:info:list', 'list-filter', 103, 1, now(), NULL, NULL, '基金列表与实时估值'),
    (1602, '基金详情查询', 1601, 1, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:info:query', '#', 103, 1, now(), NULL, NULL, '')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 1600), (1, 1601), (1, 1602)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
