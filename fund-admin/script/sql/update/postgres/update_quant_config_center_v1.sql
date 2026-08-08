-- 量化配置中心 V1（PostgreSQL 17）
-- 执行顺序：在 update_fund_data_center_v1.sql 之后执行。
-- 回滚/兼容：先停止新量化任务与配置管理入口；不得删除已被任务或结果引用的发布记录。

BEGIN;

-- 历史 Stage 1 快照保留可读；此约束仅要求 D-011 之后的新估值必须携带发布血缘。
ALTER TABLE fund_estimate
    ADD COLUMN IF NOT EXISTS config_release_version bigint,
    ADD COLUMN IF NOT EXISTS config_release_checksum char(64);
ALTER TABLE fund_estimate
    DROP CONSTRAINT IF EXISTS ck_fund_estimate_config_lineage;
ALTER TABLE fund_estimate
    ADD CONSTRAINT ck_fund_estimate_config_lineage CHECK (
        config_release_version IS NOT NULL
        AND config_release_checksum ~ '^[0-9a-f]{64}$'
    ) NOT VALID;
ALTER TABLE fund_estimate
    DROP CONSTRAINT IF EXISTS uk_fund_estimate_code_time,
    DROP CONSTRAINT IF EXISTS uk_fund_estimate_code_time_release;
ALTER TABLE fund_estimate
    ADD CONSTRAINT uk_fund_estimate_code_time_release
        UNIQUE (fund_code, estimate_time, config_release_version);
CREATE INDEX IF NOT EXISTS idx_fund_estimate_release
    ON fund_estimate (config_release_version, config_release_checksum, fund_code, estimate_time DESC);
COMMENT ON COLUMN fund_estimate.config_release_version IS '生成估值时固定的量化配置发布版本；旧快照可为空';
COMMENT ON COLUMN fund_estimate.config_release_checksum IS '生成估值时固定的量化配置发布校验和；旧快照可为空';

CREATE TABLE IF NOT EXISTS quant_config_version (
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
    CONSTRAINT ck_quant_config_version_code CHECK (
        config_code IN (
            'GLOBAL_CONVENTIONS', 'ESTIMATE', 'TREND', 'MOVING_AVERAGE', 'RSI_MACD',
            'NAV_POSITION', 'FACTOR', 'FUND_RISK', 'PORTFOLIO_RISK', 'BACKTEST'
        )
    ),
    CONSTRAINT ck_quant_config_version_schema CHECK (schema_version > 0),
    CONSTRAINT ck_quant_config_version_status CHECK (status IN ('DRAFT', 'VALIDATED', 'PUBLISHED')),
    CONSTRAINT ck_quant_config_version_checksum CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_quant_config_version_json CHECK (jsonb_typeof(config_json) = 'object')
);

CREATE INDEX IF NOT EXISTS idx_quant_config_version_code_status
    ON quant_config_version (config_code, status, config_version DESC);
CREATE INDEX IF NOT EXISTS idx_quant_config_version_effective
    ON quant_config_version (effective_from DESC NULLS LAST);

CREATE SEQUENCE IF NOT EXISTS quant_config_release_version_seq START WITH 1 INCREMENT BY 1;

COMMENT ON TABLE quant_config_version IS '量化配置分组不可变版本；仅 DRAFT 可编辑';
COMMENT ON COLUMN quant_config_version.config_json IS '规范化前业务 JSON；校验和按确定性规范化 JSON 计算';
COMMENT ON COLUMN quant_config_version.revision IS '草稿编辑乐观锁版本';

CREATE TABLE IF NOT EXISTS quant_config_release (
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
    CONSTRAINT fk_quant_config_release_rollback
        FOREIGN KEY (rollback_of_release_version) REFERENCES quant_config_release (release_version)
);

CREATE INDEX IF NOT EXISTS idx_quant_config_release_effective
    ON quant_config_release (effective_from DESC, release_version DESC);

ALTER TABLE fund_estimate
    DROP CONSTRAINT IF EXISTS fk_fund_estimate_config_release;
ALTER TABLE fund_estimate
    ADD CONSTRAINT fk_fund_estimate_config_release
        FOREIGN KEY (config_release_version) REFERENCES quant_config_release (release_version) NOT VALID;

COMMENT ON TABLE quant_config_release IS '量化配置原子发布清单；回滚通过更高发布版本重新引用旧配置';

CREATE TABLE IF NOT EXISTS quant_config_release_item (
    id                 bigint          PRIMARY KEY,
    release_id         bigint          NOT NULL,
    config_code        varchar(32)     NOT NULL,
    config_version_id  bigint          NOT NULL,
    config_version     integer         NOT NULL,
    config_checksum    char(64)        NOT NULL,
    schema_version     integer         NOT NULL,
    create_time        timestamptz     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quant_config_release_item UNIQUE (release_id, config_code),
    CONSTRAINT fk_quant_config_release_item_release
        FOREIGN KEY (release_id) REFERENCES quant_config_release (id),
    CONSTRAINT fk_quant_config_release_item_version
        FOREIGN KEY (config_version_id) REFERENCES quant_config_version (id),
    CONSTRAINT ck_quant_config_release_item_checksum CHECK (config_checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX IF NOT EXISTS idx_quant_config_release_item_version
    ON quant_config_release_item (config_code, config_version, release_id);

COMMENT ON TABLE quant_config_release_item IS '发布清单中配置版本和校验和的冗余快照，用于跨端精确校验';

CREATE OR REPLACE FUNCTION prevent_quant_config_version_mutation()
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

DROP TRIGGER IF EXISTS trg_quant_config_version_immutable ON quant_config_version;
CREATE TRIGGER trg_quant_config_version_immutable
BEFORE UPDATE OR DELETE ON quant_config_version
FOR EACH ROW EXECUTE FUNCTION prevent_quant_config_version_mutation();

CREATE OR REPLACE FUNCTION prevent_quant_config_release_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'published configuration release is immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quant_config_release_immutable ON quant_config_release;
CREATE TRIGGER trg_quant_config_release_immutable
BEFORE UPDATE OR DELETE ON quant_config_release
FOR EACH ROW EXECUTE FUNCTION prevent_quant_config_release_mutation();

DROP TRIGGER IF EXISTS trg_quant_config_release_item_immutable ON quant_config_release_item;
CREATE TRIGGER trg_quant_config_release_item_immutable
BEFORE UPDATE OR DELETE ON quant_config_release_item
FOR EACH ROW EXECUTE FUNCTION prevent_quant_config_release_mutation();

-- fund_quant_reader 由部署流程创建。角色缺失时本迁移保持可重复执行；创建后需重跑本段授权。
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

INSERT INTO sys_menu (
    menu_id, menu_name, parent_id, order_num, path, component, query_param,
    is_frame, is_cache, menu_type, visible, status, perms, icon,
    create_dept, create_by, create_time, update_by, update_time, remark
) VALUES
    (17010, '量化配置', 17000, 3, 'config', 'fund/config/index', NULL, '1', '0', 'C', '0', '0', 'fund:config:list', 'settings-2', 103, 1, now(), NULL, NULL, '版本化量化配置管理'),
    (17011, '量化配置查询', 17010, 1, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:query', '#', 103, 1, now(), NULL, NULL, ''),
    (17012, '量化配置编辑', 17010, 2, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:edit', '#', 103, 1, now(), NULL, NULL, ''),
    (17013, '量化配置校验', 17010, 3, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:validate', '#', 103, 1, now(), NULL, NULL, ''),
    (17014, '量化配置发布', 17010, 4, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:publish', '#', 103, 1, now(), NULL, NULL, ''),
    (17015, '量化配置回滚', 17010, 5, '#', '', NULL, '1', '0', 'F', '0', '0', 'fund:config:rollback', '#', 103, 1, now(), NULL, NULL, '')
ON CONFLICT (menu_id) DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
VALUES (1, 17010), (1, 17011), (1, 17012), (1, 17013), (1, 17014), (1, 17015)
ON CONFLICT (role_id, menu_id) DO NOTHING;

COMMIT;
