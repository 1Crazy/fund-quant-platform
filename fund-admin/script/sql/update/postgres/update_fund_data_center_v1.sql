-- 基金数据中心 V1 forward migration（PostgreSQL 17）
--
-- 兼容/回滚说明：
-- 1. 本脚本只做向前兼容变更：新增可空列或带安全默认值的 NOT NULL 列，新增表、索引、注释和菜单权限。
-- 2. `fund_holding` 若已存在旧列 `security_code`/`security_name`/`holding_ratio`，会条件重命名为
--    `stock_code`/`stock_name`/`disclosed_weight`，旧约束名称也会同步重命名，避免 fresh baseline 与升级路径冲突。
-- 3. 需要业务回滚时，优先关闭同步任务和管理入口，保留新增列/表以便继续读取最后成功数据并支持审计；
--    若必须结构回退，应先确认没有下游引用新增 `data_version`、`fetch_batch_id` 和同步/质量表后，再人工备份并删除新增对象。
-- 4. 本变更不引入 Flyway 或 Liquibase，继续沿用 `fund-admin/script/sql/update/postgres/` 下的有序 SQL。

BEGIN;

-- ----------------------------
-- 共享基金主数据/净值表补充血缘、版本和质量字段
-- ----------------------------
ALTER TABLE fund_info
    ADD COLUMN IF NOT EXISTS business_date date,
    ADD COLUMN IF NOT EXISTS fetch_batch_id varchar(64),
    ADD COLUMN IF NOT EXISTS data_version varchar(64) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS checksum varchar(128),
    ADD COLUMN IF NOT EXISTS quality_status varchar(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS quality_reason varchar(256);

ALTER TABLE fund_nav
    ADD COLUMN IF NOT EXISTS source_time timestamptz,
    ADD COLUMN IF NOT EXISTS fetch_batch_id varchar(64),
    ADD COLUMN IF NOT EXISTS data_version varchar(64) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS checksum varchar(128),
    ADD COLUMN IF NOT EXISTS quality_status varchar(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS quality_reason varchar(256);

CREATE INDEX IF NOT EXISTS idx_fund_info_quality_version
    ON fund_info (quality_status, data_version);
CREATE INDEX IF NOT EXISTS idx_fund_info_code_version
    ON fund_info (fund_code, data_version);
CREATE INDEX IF NOT EXISTS idx_fund_info_batch
    ON fund_info (fetch_batch_id);
CREATE INDEX IF NOT EXISTS idx_fund_nav_code_date_version
    ON fund_nav (fund_code, nav_date DESC, data_version);
CREATE INDEX IF NOT EXISTS idx_fund_nav_batch
    ON fund_nav (fetch_batch_id);
CREATE INDEX IF NOT EXISTS idx_fund_nav_quality
    ON fund_nav (quality_status, nav_date DESC);

COMMENT ON COLUMN fund_info.business_date IS '业务日期，目录数据按同步发布日期或来源有效日期记录';
COMMENT ON COLUMN fund_info.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_info.data_version IS '数据版本，纠错或重抓生成新版本';
COMMENT ON COLUMN fund_info.checksum IS '标准化记录校验和';
COMMENT ON COLUMN fund_info.quality_status IS '质量状态：NORMAL、PARTIAL、EMPTY、STALE、FAILED';
COMMENT ON COLUMN fund_info.quality_reason IS '质量状态原因摘要';
COMMENT ON COLUMN fund_nav.source_time IS '来源数据时间';
COMMENT ON COLUMN fund_nav.fetch_batch_id IS '抓取批次 ID';
COMMENT ON COLUMN fund_nav.data_version IS '数据版本，纠错或重抓生成新版本';
COMMENT ON COLUMN fund_nav.checksum IS '标准化记录校验和';
COMMENT ON COLUMN fund_nav.quality_status IS '质量状态：NORMAL、PARTIAL、EMPTY、STALE、FAILED';
COMMENT ON COLUMN fund_nav.quality_reason IS '质量状态原因摘要';

-- ----------------------------
-- fund_holding 旧结构兼容重命名
-- ----------------------------
DO $$
BEGIN
    IF to_regclass('public.fund_holding') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'fund_holding' AND column_name = 'security_code'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'fund_holding' AND column_name = 'stock_code'
        ) THEN
            ALTER TABLE fund_holding RENAME COLUMN security_code TO stock_code;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'fund_holding' AND column_name = 'security_name'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'fund_holding' AND column_name = 'stock_name'
        ) THEN
            ALTER TABLE fund_holding RENAME COLUMN security_name TO stock_name;
        END IF;

        IF EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'fund_holding' AND column_name = 'holding_ratio'
        ) AND NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = 'fund_holding' AND column_name = 'disclosed_weight'
        ) THEN
            ALTER TABLE fund_holding RENAME COLUMN holding_ratio TO disclosed_weight;
        END IF;

        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'fund_holding'::regclass AND conname = 'uk_fund_holding_report_security'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'fund_holding'::regclass AND conname = 'uk_fund_holding_report_stock'
        ) THEN
            ALTER TABLE fund_holding RENAME CONSTRAINT uk_fund_holding_report_security TO uk_fund_holding_report_stock;
        END IF;

        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'fund_holding'::regclass AND conname = 'ck_fund_holding_ratio'
        ) AND NOT EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = 'fund_holding'::regclass AND conname = 'ck_fund_holding_weight'
        ) THEN
            ALTER TABLE fund_holding RENAME CONSTRAINT ck_fund_holding_ratio TO ck_fund_holding_weight;
        END IF;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS fund_holding (
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

ALTER TABLE fund_holding
    ADD COLUMN IF NOT EXISTS stock_code varchar(24),
    ADD COLUMN IF NOT EXISTS stock_name varchar(160),
    ADD COLUMN IF NOT EXISTS disclosed_weight numeric(12, 6),
    ADD COLUMN IF NOT EXISTS market_value numeric(20, 4),
    ADD COLUMN IF NOT EXISTS holding_rank integer,
    ADD COLUMN IF NOT EXISTS source varchar(32) NOT NULL DEFAULT 'AKSHARE',
    ADD COLUMN IF NOT EXISTS source_time timestamptz,
    ADD COLUMN IF NOT EXISTS fetch_batch_id varchar(64),
    ADD COLUMN IF NOT EXISTS data_version varchar(64) NOT NULL DEFAULT 'legacy',
    ADD COLUMN IF NOT EXISTS checksum varchar(128),
    ADD COLUMN IF NOT EXISTS quality_status varchar(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS quality_reason varchar(256);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'fund_holding'::regclass AND conname = 'uk_fund_holding_report_stock'
    ) THEN
        ALTER TABLE fund_holding ADD CONSTRAINT uk_fund_holding_report_stock UNIQUE (fund_code, report_date, stock_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'fund_holding'::regclass AND conname = 'fk_fund_holding_code'
    ) THEN
        ALTER TABLE fund_holding ADD CONSTRAINT fk_fund_holding_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'fund_holding'::regclass AND conname = 'ck_fund_holding_weight'
    ) THEN
        ALTER TABLE fund_holding ADD CONSTRAINT ck_fund_holding_weight CHECK (disclosed_weight BETWEEN 0 AND 100);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fund_holding_code_report
    ON fund_holding (fund_code, report_date DESC);
CREATE INDEX IF NOT EXISTS idx_fund_holding_code_report_version
    ON fund_holding (fund_code, report_date DESC, data_version);
CREATE INDEX IF NOT EXISTS idx_fund_holding_report_version
    ON fund_holding (report_date DESC, data_version);
CREATE INDEX IF NOT EXISTS idx_fund_holding_batch
    ON fund_holding (fetch_batch_id);
CREATE INDEX IF NOT EXISTS idx_fund_holding_quality
    ON fund_holding (quality_status, report_date DESC);

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

-- ----------------------------
-- 同步运行与数据质量问题
-- ----------------------------
CREATE TABLE IF NOT EXISTS fund_sync_run (
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

ALTER TABLE fund_sync_run
    ADD COLUMN IF NOT EXISTS source varchar(32) NOT NULL DEFAULT 'AKSHARE',
    ADD COLUMN IF NOT EXISTS source_time timestamptz,
    ADD COLUMN IF NOT EXISTS business_date date,
    ADD COLUMN IF NOT EXISTS quality_status varchar(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS checksum varchar(128),
    ADD COLUMN IF NOT EXISTS duration_ms bigint,
    ADD COLUMN IF NOT EXISTS upstream_latency_ms bigint,
    ADD COLUMN IF NOT EXISTS stale_count integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS cache_invalidated_count integer NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'fund_sync_run'::regclass AND conname = 'ck_fund_sync_run_quality'
    ) THEN
        ALTER TABLE fund_sync_run
            ADD CONSTRAINT ck_fund_sync_run_quality
            CHECK (quality_status IN ('NORMAL', 'PARTIAL', 'EMPTY', 'STALE', 'FAILED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fund_sync_run_dataset_state
    ON fund_sync_run (dataset, state, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_fund_sync_run_scope
    ON fund_sync_run (dataset, scope_type, scope_value);
CREATE INDEX IF NOT EXISTS idx_fund_sync_run_version
    ON fund_sync_run (data_version);
CREATE INDEX IF NOT EXISTS idx_fund_sync_run_dataset_business_version
    ON fund_sync_run (dataset, business_date DESC, data_version);

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

CREATE TABLE IF NOT EXISTS fund_data_quality_issue (
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

ALTER TABLE fund_data_quality_issue
    ADD COLUMN IF NOT EXISTS source varchar(32) NOT NULL DEFAULT 'AKSHARE',
    ADD COLUMN IF NOT EXISTS source_time timestamptz,
    ADD COLUMN IF NOT EXISTS business_date date,
    ADD COLUMN IF NOT EXISTS data_version varchar(64),
    ADD COLUMN IF NOT EXISTS checksum varchar(128),
    ADD COLUMN IF NOT EXISTS quality_status varchar(16) NOT NULL DEFAULT 'FAILED';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'fund_data_quality_issue'::regclass AND conname = 'uk_fund_quality_issue_key'
    ) THEN
        ALTER TABLE fund_data_quality_issue
            ADD CONSTRAINT uk_fund_quality_issue_key UNIQUE (dataset, fetch_batch_id, record_key, reason_code);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'fund_data_quality_issue'::regclass AND conname = 'ck_fund_quality_issue_quality'
    ) THEN
        ALTER TABLE fund_data_quality_issue
            ADD CONSTRAINT ck_fund_quality_issue_quality
            CHECK (quality_status IN ('NORMAL', 'PARTIAL', 'EMPTY', 'STALE', 'FAILED'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_fund_quality_dataset_batch
    ON fund_data_quality_issue (dataset, fetch_batch_id);
CREATE INDEX IF NOT EXISTS idx_fund_quality_dataset_business_version
    ON fund_data_quality_issue (dataset, business_date DESC, data_version);
CREATE INDEX IF NOT EXISTS idx_fund_quality_reason
    ON fund_data_quality_issue (reason_code, detected_at DESC);
CREATE INDEX IF NOT EXISTS idx_fund_quality_record
    ON fund_data_quality_issue (dataset, record_key);

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

-- ----------------------------
-- 基金数据中心菜单与权限
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
    (17006, '基金同步重试', 17003, 3, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:sync:retry', '#', 103, 1, now(), NULL, NULL, '')
ON CONFLICT (menu_id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    parent_id = EXCLUDED.parent_id,
    order_num = EXCLUDED.order_num,
    path = EXCLUDED.path,
    component = EXCLUDED.component,
    perms = EXCLUDED.perms,
    icon = EXCLUDED.icon,
    remark = EXCLUDED.remark,
    update_time = now();

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 17000), (1, 17001), (1, 17002), (1, 17003), (1, 17004), (1, 17005), (1, 17006)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
