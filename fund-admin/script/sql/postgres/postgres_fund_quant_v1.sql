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
    business_date   date,
    fetch_batch_id  varchar(64),
    data_version    varchar(64)     NOT NULL DEFAULT 'legacy',
    checksum        varchar(128),
    quality_status  varchar(16)     NOT NULL DEFAULT 'NORMAL',
    quality_reason  varchar(256),
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
COMMENT ON COLUMN fund_info.business_date IS '业务日期，目录数据按同步发布日期或来源有效日期记录';
COMMENT ON COLUMN fund_info.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_info.data_version IS '数据版本，纠错或重抓生成新版本';
COMMENT ON COLUMN fund_info.checksum IS '标准化记录校验和';
COMMENT ON COLUMN fund_info.quality_status IS '质量状态：NORMAL、PARTIAL、EMPTY、STALE、FAILED';
COMMENT ON COLUMN fund_info.quality_reason IS '质量状态原因摘要';

CREATE INDEX idx_fund_info_name ON fund_info (fund_name);
CREATE INDEX idx_fund_info_type_status ON fund_info (fund_type, status) WHERE del_flag = 0;
CREATE INDEX idx_fund_info_quality_version ON fund_info (quality_status, data_version);
CREATE INDEX idx_fund_info_code_version ON fund_info (fund_code, data_version);
CREATE INDEX idx_fund_info_batch ON fund_info (fetch_batch_id);

CREATE TABLE fund_nav (
    id              bigint          PRIMARY KEY,
    fund_code       varchar(12)     NOT NULL,
    nav_date        date            NOT NULL,
    unit_nav        numeric(18, 6)  NOT NULL,
    accumulated_nav numeric(18, 6),
    daily_growth_rate numeric(12, 6),
    source          varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_time     timestamptz,
    fetch_batch_id  varchar(64),
    data_version    varchar(64)     NOT NULL DEFAULT 'legacy',
    checksum        varchar(128),
    quality_status  varchar(16)     NOT NULL DEFAULT 'NORMAL',
    quality_reason  varchar(256),
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_nav_code_date UNIQUE (fund_code, nav_date),
    CONSTRAINT fk_fund_nav_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code)
);

COMMENT ON TABLE fund_nav IS '基金历史净值（跨租户共享）';
COMMENT ON COLUMN fund_nav.source_time IS '来源数据时间';
COMMENT ON COLUMN fund_nav.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_nav.data_version IS '数据版本，纠错或重抓生成新版本';
COMMENT ON COLUMN fund_nav.checksum IS '标准化记录校验和';
COMMENT ON COLUMN fund_nav.quality_status IS '质量状态：NORMAL、PARTIAL、EMPTY、STALE、FAILED';
COMMENT ON COLUMN fund_nav.quality_reason IS '质量状态原因摘要';
CREATE INDEX idx_fund_nav_code_date_desc ON fund_nav (fund_code, nav_date DESC);
CREATE INDEX idx_fund_nav_code_date_version ON fund_nav (fund_code, nav_date DESC, data_version);
CREATE INDEX idx_fund_nav_batch ON fund_nav (fetch_batch_id);
CREATE INDEX idx_fund_nav_quality ON fund_nav (quality_status, nav_date DESC);

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
    status_reason   varchar(256),
    holding_coverage_rate numeric(8, 4),
    quote_coverage_rate numeric(8, 4),
    missing_quote_count integer,
    quote_time      timestamptz,
    holding_report_date date,
    holding_report_period varchar(64),
    input_data_version varchar(128),
    algorithm_version varchar(64),
    trade_date      date,
    config_release_version bigint    NOT NULL,
    config_release_checksum char(64) NOT NULL,
    estimate_config_version bigint,
    estimate_config_checksum char(64),
    create_dept     bigint,
    create_by       bigint,
    create_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by       bigint,
    update_time     timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_estimate_code_time_release UNIQUE (fund_code, estimate_time, config_release_version),
    CONSTRAINT fk_fund_estimate_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_estimate_source_status CHECK (source_status IN ('NORMAL', 'PARTIAL', 'UNSUPPORTED', 'STALE', 'FAILED', 'UPSTREAM_FAILED')),
    CONSTRAINT ck_fund_estimate_config_checksum CHECK (config_release_checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_fund_estimate_holding_coverage CHECK (holding_coverage_rate IS NULL OR holding_coverage_rate BETWEEN 0 AND 100),
    CONSTRAINT ck_fund_estimate_quote_coverage CHECK (quote_coverage_rate IS NULL OR quote_coverage_rate BETWEEN 0 AND 100),
    CONSTRAINT ck_fund_estimate_missing_quote_count CHECK (missing_quote_count IS NULL OR missing_quote_count >= 0),
    CONSTRAINT ck_fund_estimate_group_checksum CHECK (estimate_config_checksum IS NULL OR estimate_config_checksum ~ '^[0-9a-f]{64}$')
);

COMMENT ON TABLE fund_estimate IS '基金盘中估值快照（跨租户共享）';
CREATE INDEX idx_fund_estimate_code_time_desc ON fund_estimate (fund_code, estimate_time DESC);
CREATE INDEX idx_fund_estimate_release ON fund_estimate (config_release_version, config_release_checksum, fund_code, estimate_time DESC);
CREATE INDEX idx_fund_estimate_release_trade_date ON fund_estimate (config_release_version, config_release_checksum, trade_date DESC, fund_code);
CREATE INDEX idx_fund_estimate_retention ON fund_estimate (estimate_time ASC, fund_code);

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
    id                  bigint          PRIMARY KEY,
    fund_code           varchar(12)     NOT NULL,
    report_date         date            NOT NULL,
    stock_code          varchar(24)     NOT NULL,
    stock_name          varchar(160)    NOT NULL,
    disclosed_weight    numeric(12, 6)  NOT NULL,
    market_value        numeric(20, 4),
    holding_rank        integer,
    source              varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_time         timestamptz,
    fetch_batch_id      varchar(64),
    data_version        varchar(64)     NOT NULL DEFAULT 'legacy',
    checksum            varchar(128),
    quality_status      varchar(16)     NOT NULL DEFAULT 'NORMAL',
    quality_reason      varchar(256),
    create_dept         bigint,
    create_by           bigint,
    create_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           bigint,
    update_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_holding_report_stock UNIQUE (fund_code, report_date, stock_code),
    CONSTRAINT fk_fund_holding_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_holding_weight CHECK (disclosed_weight BETWEEN 0 AND 100)
);

COMMENT ON TABLE fund_holding IS '基金最新披露持仓（跨租户共享，非实时仓位）';
COMMENT ON COLUMN fund_holding.report_date IS '披露报告期';
COMMENT ON COLUMN fund_holding.stock_code IS '股票代码';
COMMENT ON COLUMN fund_holding.stock_name IS '股票名称';
COMMENT ON COLUMN fund_holding.disclosed_weight IS '披露持仓权重，百分数口径';
COMMENT ON COLUMN fund_holding.holding_rank IS '披露排名';
COMMENT ON COLUMN fund_holding.source_time IS '来源数据时间';
COMMENT ON COLUMN fund_holding.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_holding.data_version IS '数据版本，纠错或重抓生成新版本';
COMMENT ON COLUMN fund_holding.checksum IS '标准化记录校验和';
COMMENT ON COLUMN fund_holding.quality_status IS '质量状态：NORMAL、PARTIAL、EMPTY、STALE、FAILED';
COMMENT ON COLUMN fund_holding.quality_reason IS '质量状态原因摘要';

CREATE INDEX idx_fund_holding_code_report ON fund_holding (fund_code, report_date DESC);
CREATE INDEX idx_fund_holding_code_report_version ON fund_holding (fund_code, report_date DESC, data_version);
CREATE INDEX idx_fund_holding_report_version ON fund_holding (report_date DESC, data_version);
CREATE INDEX idx_fund_holding_batch ON fund_holding (fetch_batch_id);
CREATE INDEX idx_fund_holding_quality ON fund_holding (quality_status, report_date DESC);

CREATE TABLE fund_sync_run (
    id                  bigint          PRIMARY KEY,
    dataset             varchar(32)     NOT NULL,
    source              varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_time         timestamptz,
    business_date       date,
    scope_type          varchar(32)     NOT NULL DEFAULT 'ALL',
    scope_value         varchar(160),
    partition_key       varchar(160),
    state               varchar(24)     NOT NULL,
    quality_status      varchar(16)     NOT NULL DEFAULT 'NORMAL',
    cursor_value        varchar(256),
    fetch_batch_id      varchar(64)     NOT NULL,
    data_version        varchar(64),
    checksum            varchar(128),
    started_at          timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at         timestamptz,
    duration_ms         bigint,
    success_count       integer         NOT NULL DEFAULT 0,
    rejected_count      integer         NOT NULL DEFAULT 0,
    failed_count        integer         NOT NULL DEFAULT 0,
    retry_count         integer         NOT NULL DEFAULT 0,
    upstream_latency_ms bigint,
    stale_count         integer         NOT NULL DEFAULT 0,
    cache_invalidated_count integer      NOT NULL DEFAULT 0,
    error_code          varchar(64),
    error_message       varchar(1000),
    create_dept         bigint,
    create_by           bigint,
    create_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           bigint,
    update_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_sync_run_batch UNIQUE (fetch_batch_id),
    CONSTRAINT ck_fund_sync_run_state CHECK (state IN ('PENDING', 'RUNNING', 'PAUSED', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_fund_sync_run_quality CHECK (quality_status IN ('NORMAL', 'PARTIAL', 'EMPTY', 'STALE', 'FAILED')),
    CONSTRAINT ck_fund_sync_run_counts CHECK (
        success_count >= 0
        AND rejected_count >= 0
        AND failed_count >= 0
        AND retry_count >= 0
        AND stale_count >= 0
        AND cache_invalidated_count >= 0
    )
);

COMMENT ON TABLE fund_sync_run IS '基金数据同步运行记录';
COMMENT ON COLUMN fund_sync_run.dataset IS '同步数据集：FUND_INFO、FUND_NAV、FUND_HOLDING 等';
COMMENT ON COLUMN fund_sync_run.source IS '数据来源';
COMMENT ON COLUMN fund_sync_run.source_time IS '来源批次时间';
COMMENT ON COLUMN fund_sync_run.business_date IS '业务日期';
COMMENT ON COLUMN fund_sync_run.scope_type IS '同步范围类型：ALL、FUND_CODE、DATE_RANGE、PARTITION';
COMMENT ON COLUMN fund_sync_run.scope_value IS '同步范围值';
COMMENT ON COLUMN fund_sync_run.partition_key IS '分片键';
COMMENT ON COLUMN fund_sync_run.state IS '运行状态';
COMMENT ON COLUMN fund_sync_run.quality_status IS '本批次发布质量状态';
COMMENT ON COLUMN fund_sync_run.cursor_value IS '可续跑游标';
COMMENT ON COLUMN fund_sync_run.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_sync_run.data_version IS '发布数据版本';
COMMENT ON COLUMN fund_sync_run.checksum IS '批次摘要校验和';
COMMENT ON COLUMN fund_sync_run.duration_ms IS '同步耗时，毫秒';
COMMENT ON COLUMN fund_sync_run.upstream_latency_ms IS '上游调用累计耗时，毫秒';
COMMENT ON COLUMN fund_sync_run.stale_count IS '过期数据数量';
COMMENT ON COLUMN fund_sync_run.cache_invalidated_count IS '缓存失效数量';
COMMENT ON COLUMN fund_sync_run.error_message IS '脱敏错误摘要';

CREATE INDEX idx_fund_sync_run_dataset_state ON fund_sync_run (dataset, state, started_at DESC);
CREATE INDEX idx_fund_sync_run_scope ON fund_sync_run (dataset, scope_type, scope_value);
CREATE INDEX idx_fund_sync_run_version ON fund_sync_run (data_version);
CREATE INDEX idx_fund_sync_run_dataset_business_version ON fund_sync_run (dataset, business_date DESC, data_version);

CREATE TABLE fund_data_quality_issue (
    id                  bigint          PRIMARY KEY,
    sync_run_id         bigint,
    dataset             varchar(32)     NOT NULL,
    source              varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_time         timestamptz,
    business_date       date,
    fetch_batch_id      varchar(64),
    data_version        varchar(64),
    checksum            varchar(128),
    record_key          varchar(256)    NOT NULL,
    quality_status      varchar(16)     NOT NULL DEFAULT 'FAILED',
    reason_code         varchar(64)     NOT NULL,
    raw_summary         varchar(1000),
    detected_at         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issue_status        varchar(16)     NOT NULL DEFAULT 'OPEN',
    create_dept         bigint,
    create_by           bigint,
    create_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           bigint,
    update_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fund_quality_sync_run FOREIGN KEY (sync_run_id) REFERENCES fund_sync_run (id),
    CONSTRAINT uk_fund_quality_issue_key UNIQUE (dataset, fetch_batch_id, record_key, reason_code),
    CONSTRAINT ck_fund_quality_issue_quality CHECK (quality_status IN ('NORMAL', 'PARTIAL', 'EMPTY', 'STALE', 'FAILED')),
    CONSTRAINT ck_fund_quality_status CHECK (issue_status IN ('OPEN', 'IGNORED', 'RESOLVED'))
);

COMMENT ON TABLE fund_data_quality_issue IS '基金数据质量问题记录';
COMMENT ON COLUMN fund_data_quality_issue.sync_run_id IS '关联同步运行 ID';
COMMENT ON COLUMN fund_data_quality_issue.dataset IS '问题所属数据集';
COMMENT ON COLUMN fund_data_quality_issue.source IS '数据来源';
COMMENT ON COLUMN fund_data_quality_issue.source_time IS '来源记录时间';
COMMENT ON COLUMN fund_data_quality_issue.business_date IS '业务日期';
COMMENT ON COLUMN fund_data_quality_issue.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_data_quality_issue.data_version IS '关联数据版本';
COMMENT ON COLUMN fund_data_quality_issue.checksum IS '问题记录标准化摘要校验和';
COMMENT ON COLUMN fund_data_quality_issue.record_key IS '问题记录业务键';
COMMENT ON COLUMN fund_data_quality_issue.quality_status IS '问题导致的数据质量状态';
COMMENT ON COLUMN fund_data_quality_issue.reason_code IS '问题原因代码';
COMMENT ON COLUMN fund_data_quality_issue.raw_summary IS '脱敏原始值摘要';
COMMENT ON COLUMN fund_data_quality_issue.detected_at IS '检测时间';
COMMENT ON COLUMN fund_data_quality_issue.issue_status IS '问题处理状态';

CREATE INDEX idx_fund_quality_dataset_batch ON fund_data_quality_issue (dataset, fetch_batch_id);
CREATE INDEX idx_fund_quality_dataset_business_version ON fund_data_quality_issue (dataset, business_date DESC, data_version);
CREATE INDEX idx_fund_quality_reason ON fund_data_quality_issue (reason_code, detected_at DESC);
CREATE INDEX idx_fund_quality_record ON fund_data_quality_issue (dataset, record_key);

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

-- 量化配置中心：配置版本仅允许在 DRAFT 状态编辑，发布清单始终不可变。
CREATE TABLE quant_config_version (
    id                 bigint          PRIMARY KEY,
    config_code        varchar(32)     NOT NULL,
    config_version     integer         NOT NULL,
    schema_version     integer         NOT NULL,
    status             varchar(16)     NOT NULL DEFAULT 'DRAFT',
    config_json        jsonb           NOT NULL,
    checksum           char(64)        NOT NULL,
    effective_from     timestamptz,
    revision           bigint          NOT NULL DEFAULT 0,
    create_dept        bigint,
    create_by          bigint,
    create_time        timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          bigint,
    update_time        timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark             varchar(500),
    CONSTRAINT uk_quant_config_version UNIQUE (config_code, config_version),
    CONSTRAINT ck_quant_config_version_code CHECK (config_code IN (
        'GLOBAL_CONVENTIONS', 'ESTIMATE', 'TREND', 'MOVING_AVERAGE', 'RSI_MACD',
        'NAV_POSITION', 'FACTOR', 'FUND_RISK', 'PORTFOLIO_RISK', 'BACKTEST'
    )),
    CONSTRAINT ck_quant_config_version_schema CHECK (schema_version > 0),
    CONSTRAINT ck_quant_config_version_status CHECK (status IN ('DRAFT', 'VALIDATED', 'PUBLISHED')),
    CONSTRAINT ck_quant_config_version_checksum CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_quant_config_version_json CHECK (jsonb_typeof(config_json) = 'object')
);
CREATE INDEX idx_quant_config_version_code_status ON quant_config_version (config_code, status, config_version DESC);
CREATE INDEX idx_quant_config_version_effective ON quant_config_version (effective_from DESC NULLS LAST);

CREATE SEQUENCE quant_config_release_version_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE quant_config_release (
    id                 bigint          PRIMARY KEY,
    release_version    bigint          NOT NULL,
    status             varchar(16)     NOT NULL DEFAULT 'PUBLISHED',
    checksum           char(64)        NOT NULL,
    effective_from     timestamptz     NOT NULL,
    published_by       bigint,
    published_at       timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rollback_of_release_version bigint,
    change_summary     varchar(500),
    create_dept        bigint,
    create_by          bigint,
    create_time        timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by          bigint,
    update_time        timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark             varchar(500),
    CONSTRAINT uk_quant_config_release_version UNIQUE (release_version),
    CONSTRAINT ck_quant_config_release_status CHECK (status = 'PUBLISHED'),
    CONSTRAINT ck_quant_config_release_checksum CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT fk_quant_config_release_rollback FOREIGN KEY (rollback_of_release_version)
        REFERENCES quant_config_release (release_version)
);
CREATE INDEX idx_quant_config_release_effective ON quant_config_release (effective_from DESC, release_version DESC);

ALTER TABLE fund_estimate
    ADD CONSTRAINT fk_fund_estimate_config_release
        FOREIGN KEY (config_release_version) REFERENCES quant_config_release (release_version);

CREATE TABLE fund_nav_position (
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
CREATE INDEX idx_fund_nav_position_release_region
    ON fund_nav_position (config_release_version, config_release_checksum, nav_position_region, fund_code);
CREATE INDEX idx_fund_nav_position_release_calculated_at
    ON fund_nav_position (config_release_version, config_release_checksum, calculated_at DESC);

CREATE TABLE quant_config_release_item (
    id                 bigint          PRIMARY KEY,
    release_id         bigint          NOT NULL,
    config_code        varchar(32)     NOT NULL,
    config_version_id  bigint          NOT NULL,
    config_version     integer         NOT NULL,
    config_checksum    char(64)        NOT NULL,
    schema_version     integer         NOT NULL,
    create_time        timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quant_config_release_item UNIQUE (release_id, config_code),
    CONSTRAINT fk_quant_config_release_item_release FOREIGN KEY (release_id) REFERENCES quant_config_release (id),
    CONSTRAINT fk_quant_config_release_item_version FOREIGN KEY (config_version_id) REFERENCES quant_config_version (id),
    CONSTRAINT ck_quant_config_release_item_checksum CHECK (config_checksum ~ '^[0-9a-f]{64}$')
);
CREATE INDEX idx_quant_config_release_item_version
    ON quant_config_release_item (config_code, config_version, release_id);

CREATE FUNCTION prevent_quant_config_version_mutation()
RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'validated or published configuration version is immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'validated or published configuration version is immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.status = 'DRAFT' AND OLD.status <> 'DRAFT' THEN
        RAISE EXCEPTION 'configuration version cannot return to draft';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_quant_config_version_immutable
BEFORE UPDATE OR DELETE ON quant_config_version
FOR EACH ROW EXECUTE FUNCTION prevent_quant_config_version_mutation();

CREATE FUNCTION prevent_quant_config_release_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'published configuration release is immutable';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_quant_config_release_immutable
BEFORE UPDATE OR DELETE ON quant_config_release
FOR EACH ROW EXECUTE FUNCTION prevent_quant_config_release_mutation();
CREATE TRIGGER trg_quant_config_release_item_immutable
BEFORE UPDATE OR DELETE ON quant_config_release_item
FOR EACH ROW EXECUTE FUNCTION prevent_quant_config_release_mutation();

-- 部署账户创建 fund_quant_reader 后授予最小只读权限并施加连接与查询限制。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fund_quant_reader') THEN
        GRANT SELECT ON quant_config_version, quant_config_release, quant_config_release_item TO fund_quant_reader;
        EXECUTE 'ALTER ROLE fund_quant_reader SET default_transaction_read_only = on';
        EXECUTE 'ALTER ROLE fund_quant_reader SET statement_timeout = ''5000ms''';
        EXECUTE 'ALTER ROLE fund_quant_reader CONNECTION LIMIT 10';
    END IF;
END;
$$;

-- ----------------------------
-- 基金实时估值菜单与权限
-- 菜单 ID 使用独立号段，重复执行时通过 ON CONFLICT 保持幂等。
-- ----------------------------
INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES
    (17000, '基金中心', 0, 10, 'fund', NULL, NULL, '1', '0', 'M', '0', '0', '', 'chart-no-axes-combined', 103, 1, now(), NULL, NULL, '基金量化决策业务菜单'),
    (17001, '基金实时估值', 17000, 1, 'list', 'fund/list/index', NULL, '1', '0', 'C', '0', '0', 'fund:info:list', 'list-filter', 103, 1, now(), NULL, NULL, '基金列表与实时估值'),
    (17002, '基金详情查询', 17001, 1, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:info:query', '#', 103, 1, now(), NULL, NULL, ''),
    (17003, '基金同步管理', 17000, 2, 'sync', 'fund/sync/index', NULL, '1', '0', 'C', '0', '0', 'fund:sync:list', 'history', 103, 1, now(), NULL, NULL, '基金数据同步运行与质量问题'),
    (17004, '基金同步查询', 17003, 1, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:sync:query', '#', 103, 1, now(), NULL, NULL, ''),
    (17005, '基金同步触发', 17003, 2, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:sync:trigger', '#', 103, 1, now(), NULL, NULL, ''),
    (17006, '基金同步重试', 17003, 3, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:sync:retry', '#', 103, 1, now(), NULL, NULL, ''),
    (17010, '量化配置', 17000, 3, 'config', 'fund/config/index', NULL, '1', '0', 'C', '0', '0', 'fund:config:list', 'settings-2', 103, 1, now(), NULL, NULL, '版本化量化配置管理'),
    (17011, '量化配置查询', 17010, 1, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:query', '#', 103, 1, now(), NULL, NULL, ''),
    (17012, '量化配置编辑', 17010, 2, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:edit', '#', 103, 1, now(), NULL, NULL, ''),
    (17013, '量化配置校验', 17010, 3, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:validate', '#', 103, 1, now(), NULL, NULL, ''),
    (17014, '量化配置发布', 17010, 4, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:publish', '#', 103, 1, now(), NULL, NULL, ''),
    (17015, '量化配置回滚', 17010, 5, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:rollback', '#', 103, 1, now(), NULL, NULL, '')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 17000), (1, 17001), (1, 17002), (1, 17003), (1, 17004), (1, 17005), (1, 17006),
       (1, 17010), (1, 17011), (1, 17012), (1, 17013), (1, 17014), (1, 17015)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
