# 基金数据中心运行手册

本文覆盖 `complete-fund-data-center` 的数据库、部署参数、同步操作、可观测字段和回滚/兼容约定。

## 1. 数据库迁移

升级已有 PostgreSQL 环境时执行：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin
psql -h localhost -p 5432 -U postgres -d fund_quant \
  -f script/sql/update/postgres/update_fund_data_center_v1.sql
```

全新环境使用以下任一 baseline，二者已包含数据中心表、索引、注释和菜单权限：

- `script/sql/postgres/postgres_fund_realtime.sql`
- `script/sql/postgres/postgres_fund_quant_v1.sql`

迁移继续沿用 RuoYi-Vue-Plus 的有序 SQL 方式，不引入 Flyway 或 Liquibase。

### 关键结构

| 表 | 用途 | 关键自然键 / 查询路径 |
| --- | --- | --- |
| `fund_info` | 基金主数据当前投影 | `fund_code`，`fund_code + data_version` |
| `fund_nav` | 已确认历史净值当前投影 | `fund_code + nav_date`，`fund_code + nav_date + data_version` |
| `fund_holding` | 最新已披露持仓当前投影，非实时仓位 | `fund_code + report_date + stock_code` |
| `fund_sync_run` | 同步运行、游标、计数器和可观测指标 | `fetch_batch_id`，`dataset + state + started_at` |
| `fund_data_quality_issue` | 被拒绝或隔离的数据质量问题 | `dataset + fetch_batch_id + record_key + reason_code` |

所有当前投影表保留 `source`、来源时间、业务日期或业务报告期、`fetch_batch_id`、`data_version`、`checksum`、`quality_status` 和 `quality_reason`。

## 2. 环境变量

Java 主服务读取以下配置。生产环境必须显式配置供应方地址；开发环境的 `application-dev.yml` 默认指向本机 `fund-quant`。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `FUND_DATA_PROVIDER_BASE_URL` | dev: `http://localhost:8000`；默认配置为空 | Python 数据中心基础地址 |
| `FUND_ESTIMATE_PROVIDER_URL` | dev: `http://localhost:8000/internal/v1/data/estimate/{code}`；默认配置为空 | 估值接口地址 |
| `FUND_SYNC_ENABLED` | `true` | 数据中心同步总开关；关闭后继续读取 PostgreSQL 最后成功版本 |
| `FUND_SYNC_SCHEDULE_ENABLED` | `true` | 是否启用定时增量触发；关闭后仍可授权手动同步 |
| `FUND_SYNC_PAGE_SIZE` | `200` | 目录、NAV、持仓长批任务分页大小 |
| `FUND_SYNC_INCREMENTAL_NAV_DAYS` | `14` | 日常增量同步每只基金回补的最近 NAV 自然日范围 |
| `FUND_SYNC_RATE_LIMIT_PER_MINUTE` | `60` | Java 对供应方调用的限速预算 |
| `FUND_SYNC_RETRY_MAX_ATTEMPTS` | `3` | 可重试上游错误最大尝试次数 |
| `FUND_SYNC_RETRY_BACKOFF_INITIAL` | `2s` | 初始退避 |
| `FUND_SYNC_RETRY_BACKOFF_MAX` | `2m` | 最大退避 |
| `FUND_CACHE_INFO_TTL` | `30m` | `fund:info:{code}` TTL |
| `FUND_CACHE_NAV_TTL` | `1h` | `fund:nav:{code}:{period}` TTL |
| `FUND_CACHE_HOLDING_TTL` | `6h` | `fund:holding:{code}:{reportDate}` TTL |
| `FUND_CACHE_SYNC_STATUS_TTL` | `30s` | 同步状态摘要 TTL |

Python 服务在 `fund-quant/docker-compose.yml` 中暴露缓存和上游重试参数：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `FUND_QUANT_REDIS_URL` | `redis://redis:6379/1` | Python 侧缓存 Redis |
| `FUND_QUANT_FUND_CACHE_SECONDS` | `21600` | 基金目录/档案缓存秒数 |
| `FUND_QUANT_NAV_CACHE_SECONDS` | `1800` | NAV 缓存秒数 |
| `FUND_QUANT_HOLDING_CACHE_SECONDS` | `21600` | 持仓缓存秒数 |
| `FUND_QUANT_UPSTREAM_MAX_RETRIES` | `2` | AkShare 调用最大重试次数 |
| `FUND_QUANT_UPSTREAM_RETRY_BASE_SECONDS` | `0.5` | Python 上游重试基础退避秒数 |
| `FUND_QUANT_UPSTREAM_RETRY_AFTER_SECONDS` | `30` | 受限或临时失败后的建议重试间隔 |

## 3. 同步操作

### 全量初始化

1. 确认 PostgreSQL、Redis、SnailJob、Java 主服务和 `fund-quant` 已启动。
2. 确认 `FUND_SYNC_ENABLED=true`，并按 AkShare 稳定性调整 `FUND_SYNC_PAGE_SIZE` 和 `FUND_SYNC_RATE_LIMIT_PER_MINUTE`。
3. 通过同步管理入口或授权 API 提交全量目录初始化、历史 NAV 回填和持仓回填任务。
4. 长批任务必须由 SnailJob 承载，Java 负责创建 `fund_sync_run`，持久化 `cursor_value`、计数器和失败摘要。
5. 每个分区成功发布后更新对应当前投影表，并在事务提交后失效 `fund:info:*`、`fund:nav:*`、`fund:holding:*`。

### 日常增量

日常增量按数据集拆分运行：

1. 更新基金目录和变更基金档案。
2. 同步近期已确认 NAV 日期。
3. 刷新到期的最新披露持仓。
4. 写入 `fund_sync_run` 的 `success_count`、`rejected_count`、`failed_count`、`retry_count`、`stale_count` 和 `cache_invalidated_count`。

### 重试处理

- `DATA_PROVIDER_UNAVAILABLE`、限流、网络超时等可重试错误按指数退避处理，不立即重放已完成分区。
- `DATA_PROVIDER_SCHEMA_CHANGED` 属于契约漂移，必须标记失败或部分成功，禁止把该批次发布为 `NORMAL`。
- 单条无效记录写入 `fund_data_quality_issue`，同批有效记录可以提交，批次以 `PARTIAL_SUCCESS` 结束。

## 4. 可观测字段

同步日志和指标至少使用以下字段，字段名与 `fund_sync_run` 保持一致：

- `dataset`
- `source`
- `business_date`
- `scope_type`
- `scope_value`
- `partition_key`
- `state`
- `quality_status`
- `cursor_value`
- `fetch_batch_id`
- `data_version`
- `duration_ms`
- `success_count`
- `rejected_count`
- `failed_count`
- `retry_count`
- `upstream_latency_ms`
- `stale_count`
- `cache_invalidated_count`
- `error_code`
- `error_message`

质量问题日志至少包含：

- `dataset`
- `source`
- `business_date`
- `fetch_batch_id`
- `data_version`
- `record_key`
- `quality_status`
- `reason_code`
- `raw_summary`
- `detected_at`
- `issue_status`

## 5. 禁用同步与回滚

业务回滚优先关闭同步，而不是删除结构：

```bash
export FUND_SYNC_ENABLED=false
```

关闭后：

- Java 不再提交新的全量、增量或手动同步任务。
- 查询继续读取 PostgreSQL 当前投影中的最后成功版本。
- `fund_sync_run` 和 `fund_data_quality_issue` 保留用于审计和排障。
- Redis 可继续按 TTL 过期；需要立即切换到数据库最后版本时，手动清理 `fund:info:*`、`fund:nav:*` 和 `fund:holding:*`。

只有在确认没有下游引用 `data_version`、`fetch_batch_id`、`fund_sync_run` 和 `fund_data_quality_issue` 后，才考虑结构回退。结构回退前必须先备份，并按依赖顺序删除菜单权限、质量问题、同步运行和新增列；常规运维不建议执行结构删除。
