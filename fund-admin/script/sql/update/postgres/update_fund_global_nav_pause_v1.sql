-- 全量历史净值同步支持用户暂停后续跑。
ALTER TABLE fund_sync_run
    DROP CONSTRAINT IF EXISTS ck_fund_sync_run_state;

ALTER TABLE fund_sync_run
    ADD CONSTRAINT ck_fund_sync_run_state
    CHECK (state IN ('PENDING', 'RUNNING', 'PAUSED', 'SUCCESS', 'PARTIAL_SUCCESS', 'FAILED', 'CANCELLED'));
