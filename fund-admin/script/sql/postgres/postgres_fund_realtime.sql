-- 基金实时估值模块（PostgreSQL 17）
-- 仅包含第一阶段三张业务表，可在 RuoYi PostgreSQL 基础库上独立执行。

BEGIN;

CREATE TABLE IF NOT EXISTS fund_info (
    id                  bigint          PRIMARY KEY,
    fund_code           varchar(12)     NOT NULL,
    fund_name           varchar(160)    NOT NULL,
    fund_type           varchar(32)     NOT NULL,
    pinyin_abbr         varchar(64),
    manager_name        varchar(160),
    custodian_name      varchar(160),
    establish_date      date,
    benchmark           varchar(500),
    risk_level          varchar(16),
    fund_scale          numeric(20, 4),
    status              char(1)         NOT NULL DEFAULT '0',
    source              varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_updated_at   timestamptz,
    create_dept         bigint,
    create_by           bigint,
    create_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           bigint,
    update_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag            bigint          NOT NULL DEFAULT 0,
    CONSTRAINT uk_fund_info_code UNIQUE (fund_code),
    CONSTRAINT ck_fund_info_status CHECK (status IN ('0', '1')),
    CONSTRAINT ck_fund_info_del_flag CHECK (del_flag IN (0, 1))
);

COMMENT ON TABLE fund_info IS '基金基础信息（跨租户共享）';
COMMENT ON COLUMN fund_info.id IS '主键';
COMMENT ON COLUMN fund_info.fund_code IS '基金代码';
COMMENT ON COLUMN fund_info.fund_name IS '基金名称';
COMMENT ON COLUMN fund_info.fund_type IS '基金类型';
COMMENT ON COLUMN fund_info.fund_scale IS '基金规模，单位亿元';
COMMENT ON COLUMN fund_info.status IS '状态：0正常，1停用';

CREATE INDEX IF NOT EXISTS idx_fund_info_name ON fund_info (fund_name);
CREATE INDEX IF NOT EXISTS idx_fund_info_type_status
    ON fund_info (fund_type, status) WHERE del_flag = 0;

CREATE TABLE IF NOT EXISTS fund_nav (
    id                  bigint          PRIMARY KEY,
    fund_code           varchar(12)     NOT NULL,
    nav_date            date            NOT NULL,
    unit_nav            numeric(18, 6)  NOT NULL,
    accumulated_nav     numeric(18, 6),
    daily_growth_rate   numeric(12, 6),
    source              varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    create_dept         bigint,
    create_by           bigint,
    create_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by           bigint,
    update_time         timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_nav_code_date UNIQUE (fund_code, nav_date),
    CONSTRAINT fk_fund_nav_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code)
);

COMMENT ON TABLE fund_nav IS '基金历史净值（跨租户共享）';
COMMENT ON COLUMN fund_nav.nav_date IS '净值日期';
COMMENT ON COLUMN fund_nav.unit_nav IS '单位净值';
COMMENT ON COLUMN fund_nav.accumulated_nav IS '累计净值';
COMMENT ON COLUMN fund_nav.daily_growth_rate IS '单日增长率，百分数口径';

CREATE INDEX IF NOT EXISTS idx_fund_nav_code_date_desc
    ON fund_nav (fund_code, nav_date DESC);

CREATE TABLE IF NOT EXISTS fund_estimate (
    id                      bigint          PRIMARY KEY,
    fund_code               varchar(12)     NOT NULL,
    estimate_time           timestamptz     NOT NULL,
    estimate_nav            numeric(18, 6)  NOT NULL,
    estimate_growth_rate    numeric(12, 6),
    previous_nav            numeric(18, 6),
    previous_nav_date       date,
    source                  varchar(32)     NOT NULL DEFAULT 'AKSHARE',
    source_status           varchar(16)     NOT NULL DEFAULT 'NORMAL',
    create_dept             bigint,
    create_by               bigint,
    create_time             timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_by               bigint,
    update_time             timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_fund_estimate_code_time UNIQUE (fund_code, estimate_time),
    CONSTRAINT fk_fund_estimate_code FOREIGN KEY (fund_code) REFERENCES fund_info (fund_code),
    CONSTRAINT ck_fund_estimate_status CHECK (source_status IN ('NORMAL', 'STALE', 'FAILED'))
);

COMMENT ON TABLE fund_estimate IS '基金盘中估值快照（跨租户共享）';
COMMENT ON COLUMN fund_estimate.estimate_time IS '估值时间';
COMMENT ON COLUMN fund_estimate.estimate_nav IS '盘中估算净值';
COMMENT ON COLUMN fund_estimate.estimate_growth_rate IS '盘中估算涨跌幅，百分数口径';
COMMENT ON COLUMN fund_estimate.source_status IS '数据状态：NORMAL、STALE、FAILED';

CREATE INDEX IF NOT EXISTS idx_fund_estimate_code_time_desc
    ON fund_estimate (fund_code, estimate_time DESC);

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES
    (1600, '基金中心', 0, 10, 'fund', NULL, NULL, '1', '0', 'M', '0', '0', '', 'chart-no-axes-combined', 103, 1, now(), NULL, NULL, '基金量化决策业务菜单'),
    (1601, '基金实时估值', 1600, 1, 'list', 'fund/list/index', NULL, '1', '0', 'C', '0', '0', 'fund:info:list', 'list-filter', 103, 1, now(), NULL, NULL, '基金列表与实时估值'),
    (1602, '基金详情查询', 1601, 1, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:info:query', '#', 103, 1, now(), NULL, NULL, '')
ON CONFLICT (menu_id) DO NOTHING;

-- 默认管理员角色继承基金菜单与权限，其他角色仍通过 RuoYi 菜单管理按需授权。
INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 1600), (1, 1601), (1, 1602)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
