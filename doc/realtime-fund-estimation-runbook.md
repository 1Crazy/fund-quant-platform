# 盘中基金估值运行手册

## 范围与边界

本手册对应 OpenSpec Stage 3 `harden-realtime-fund-estimation`。盘中估值是基于最近确认 NAV、最近公开股票持仓和成分实时行情的数学估算，不是基金公司确认净值，也不产生买卖、持仓或收益建议。

- Java 是结果快照的唯一写入口；Python 不写 RuoYi 数据库。
- Python 只用 `fund_quant_reader` 在只读事务中读取 `fund_nav`、`fund_holding` 和量化配置表，不触发数据同步。
- 覆盖率、行情最大年龄、时区、舍入和算法血缘只能来自固定的量化配置发布；缓存、超时、调度和保留期只来自 `sys_config`。
- 所有本机地址使用 `localhost`，不得使用 `127.0.0.1`。

## 部署顺序

1. 完成数据中心 Stage 1 迁移与同步能力，确认 `fund_nav`、`fund_holding` 都包含 `data_version` 和 `quality_status`。
2. 依次执行 `update_quant_config_center_v1.sql`、`seed_quant_config_cross_market_v1.sql`，由有权限的操作员在 Java 管理端完成双端校验并发布 `cross-market-fund-v1`。发布必须已到达生效时间。若采用 Stage 3 的字段级精度，再执行 `seed_estimate_precision_v2.sql`，并发布仅将 `ESTIMATE` 更新为 schema v2 的新 release。
3. 创建或更新只读角色 `fund_quant_reader`，授予下列共享表的 `SELECT`：
   `quant_config_version`、`quant_config_release`、`quant_config_release_item`、`fund_info`、`fund_nav`、`fund_holding`。
   该角色必须保持 `default_transaction_read_only=on`、5 秒 `statement_timeout` 和 10 个连接上限。Stage 2 与 Stage 3 迁移分别授予对应表权限；角色在迁移后创建时，应重跑两个授权块。
4. 依次执行 `update_harden_realtime_fund_estimation_v1.sql`、`update_harden_realtime_fund_estimation_v2.sql`。它们扩展 `fund_estimate`、回填遗留快照为 `STALE`、写入运行参数，并默认关闭调度。
5. 为 Python 配置 `FUND_QUANT_CONFIG_READONLY_DSN`。它应使用上述只读角色；连接池总上限由单个共享池控制，不能另建一个独立的配置读取池。
6. 先部署 Python，再部署 Java，最后部署 Web。Java 的估值供应方地址必须指向 Python 的 `/internal/v1/data/estimate/{code}`；本机联调用 `http://localhost:8000`。
7. 通过 `GET /fund/estimate/{code}` 对具备确认 NAV、正常质量持仓和可用行情的少量基金进行灰度检查。确认结果的发布版本、校验和、持仓覆盖率、行情覆盖率、报告期和输入数据版本均已回显后，才配置热点基金并开启调度。

## 启用前门禁

1. Stage 2 发布必须是精确的活动发布版本，且 Java/Python 返回的发布版本、发布校验和、`ESTIMATE` 组版本与组校验和一致。
2. `fund.estimate.schedule.enabled` 必须先保持 `false`；`fund.estimate.schedule.hot-fund-codes` 留空。灰度成功后再由运维显式启用。
3. Stage 3 规格要求 NAV 保留 6 位小数、百分比与覆盖率保留 4 位小数。已发布 `ESTIMATE` schema v1 保持全局 6 位历史语义；执行 `seed_estimate_precision_v2.sql`、通过 Java/Python 双端校验并发布含 `ESTIMATE` schema v2 的新发布版本后，才启用新的字段级精度。不得修改 v1 或通过源码隐式推导 4 位。

## Redis 投影

- 发布提交后，Java 会写入 release 和 group 的 Redis 投影。RuoYi 的多租户缓存会自动为键添加租户前缀；默认租户的实际键为 `000000:fund:quant-config:release:{releaseVersion}` 和 `000000:fund:quant-config:group:{configCode}:{configVersion}`，不能只扫描未加前缀的 `fund:quant-config:*`。
- 未到 `effective_from` 的发布仍会有精确 release/group 投影，但不会写 `release:active`；任务固定活动版本前必须先等待生效时间，不能通过修改已发布记录强行激活。

## `sys_config` 运行参数

以下键由 Stage 3 迁移提供。修改它们不会改变已保存快照的量化语义，但会影响后续调用容量与新鲜度展示。

| 键 | 初始值 | 用途 |
| --- | ---: | --- |
| `fund.estimate.provider.connect-timeout-ms` | 2000 | Java 到 Python 的连接超时 |
| `fund.estimate.provider.read-timeout-ms` | 30000 | Java 到 Python 的读取超时 |
| `fund.estimate.cache.ttl-seconds` | 45 | Java 正常估值 Redis TTL |
| `fund.estimate.cache.closed-ttl-seconds` | 1800 | 闭市后最后成功估值 Redis TTL |
| `fund.estimate.provider-result-cache-seconds` | 15 | Python 单基金估值缓存窗口 |
| `fund.estimate.market-quote-cache-seconds` | 15 | Python 成分行情缓存窗口 |
| `fund.estimate.stale-after-seconds` | 180 | 快照仅能作为 `STALE` 返回的时长 |
| `fund.estimate.lock.wait-millis` / `lock.lease-millis` | 800 / 5000 | 单基金请求合并锁 |
| `fund.estimate.snapshot.throttle-seconds` | 300 | 同基金同发布版本常规快照最小间隔 |
| `fund.estimate.snapshot.close-time` | `15:00` | 交易日强制写入一次收盘快照；`OFF` 关闭 |
| `fund.estimate.schedule.enabled` | false | Spring Scheduler 总开关 |
| `fund.estimate.schedule.cron` | `*/30 * * * * MON-FRI` | 30 秒调度表达式 |
| `fund.estimate.schedule.zone-id` | `Asia/Shanghai` | 调度时区，不定义结果时区 |
| `fund.estimate.schedule.trading-sessions` | `09:30-11:30,13:00-15:00` | 允许自动刷新时段 |
| `fund.estimate.schedule.holidays` | 空 | 年度维护的非交易日列表，后续应迁移到数据中心交易日历 |
| `fund.estimate.schedule.hot-fund-codes` / `batch-size` | 空 / 50 | 热点范围和单批上限 |
| `fund.estimate.retention-days` | 180 | SnailJob 快照清理保留期 |

## 日常操作

### 手动刷新与状态查看

- `POST /fund/estimate/{code}/refresh` 需要 `fund:estimate:refresh` 权限，会绕过当前 Java 估值热缓存。
- `GET /fund/estimate/status` 需要 `fund:estimate:monitor` 权限，返回当前节点的交易时段、调度锁、最近批次、状态计数、最后错误和固定发布血缘。
- `NORMAL` 才表示可展示的盘中估值；`PARTIAL`、`UNSUPPORTED`、`FAILED`、`UPSTREAM_FAILED` 不得填充估值数值。`STALE` 仅表示同一发布版本的最近成功缓存或快照降级结果。

### 暂停、恢复与缓存清理

1. 暂停自动刷新：把 `fund.estimate.schedule.enabled` 设为 `false`。这不删除历史快照，查询仍可返回最后成功结果并标记新鲜度。
2. 清理某基金热缓存时，只删除 `fund:estimate:{fundCode}:*`，不删除 `fund_estimate` 历史记录；下次授权刷新会生成新键。
3. 恢复时先检查活动发布和 Python 只读连接，再设置少量热点基金，最后打开调度。不要用清空缓存代替配置或数据质量故障处理。

### 不可估值与上游故障排查

| 状态/原因 | 首先检查 |
| --- | --- |
| `UNSUPPORTED` / `DATA_CENTER_INPUT_UNAVAILABLE` | 最新 NAV、同报告期所有持仓的质量状态和数据中心同步结果 |
| `UNSUPPORTED` / `NO_DISCLOSED_EQUITY_HOLDINGS` | 是否确有公开权益持仓；不要把债券、现金或缺失持仓当作零涨跌 |
| `PARTIAL` / `INSUFFICIENT_QUOTE_COVERAGE` | 组件行情时间戳、90 秒上限和覆盖率阈值 |
| `STALE` | 同发布版本最后成功缓存/快照的估值时间和上游故障摘要 |
| `UPSTREAM_FAILED` | Python 健康检查、供应方时延、只读连接和 Java-Python 请求头 |
| `QUANT_CONFIG_*` | 精确发布版本、校验和、ESTIMATE 组校验和与生效时间；不得回退到最新版本 |

## 可观测性与保留清理

Actuator 指标采用低基数标签，不得在标签中包含基金代码、发布校验和或原始异常信息：

- `fund.estimate.provider.duration`：供应方调用耗时，按 `outcome`。
- `fund.estimate.results`：计算状态计数，按 `source_status`。
- `fund.estimate.coverage.percent`：持仓与行情覆盖率分布，按 `kind`。
- `fund.estimate.cache.requests`：缓存命中/未命中。
- `fund.estimate.stale.fallbacks`：快照降级或无快照上游失败。
- `fund.estimate.schedule.duration`：批调度耗时。
- `fund.estimate.snapshots` 与 `fund.estimate.retention.deleted`：快照节流/写入和清理数量。

在 SnailJob 中创建 `fundEstimateRetentionJob`，每次只执行一个有界批次。它删除过期的非最新快照，始终保留每只基金、每个配置发布版本的最新快照。历史回填和批量重算使用 `fundEstimateBatchRecalculationJob` 分片执行，参数为 `releaseVersion,releaseChecksum,shardIndex,shardTotal`；每个分片按基金代码游标记录进度、状态计数和固定配置血缘，发布/参数校验异常由 SnailJob 重试策略处理。不得把长批任务塞进 30 秒 Spring Scheduler。单基金显式历史重算使用 `fundEstimateRecalculationJob`，参数为 `fundCode,releaseVersion,releaseChecksum`；目标发布版本和校验和必须同时存在并精确匹配。

## 回滚

1. 关闭 `fund.estimate.schedule.enabled`。
2. 通过量化配置中心发布一个新的回滚发布版本，而不是修改或删除旧发布版本。
3. 仅清理受影响基金的新版本 Redis 缓存键；保留数据库快照及其原始发布血缘。
4. 修复后按灰度流程重新启用。不要将旧快照重标为新配置生成的结果。
