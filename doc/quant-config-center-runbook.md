# 量化配置中心运行手册

## 适用范围与事实来源

量化配置中心管理会改变数学含义的参数。PostgreSQL 的 `quant_config_version`、`quant_config_release` 与 `quant_config_release_item` 是唯一事实来源；Redis 和 Python 进程内缓存只是可重建的只读投影。

下列值属于运维配置而不是量化配置：功能开关、HTTP 超时、缓存 TTL、连接池大小、批大小与调度周期。它们可以调整运行能力，但不得改变已发布结果的数学含义。

| 配置组 | 主要内容 |
| --- | --- |
| `GLOBAL_CONVENTIONS` | UTC 存储、百分点单位、回撤符号、交易日、无风险率、`ddof`、精度和舍入、市场会话 |
| `ESTIMATE` | 公开持仓覆盖率与行情最大年龄 |
| `TREND` / `MOVING_AVERAGE` / `RSI_MACD` | 趋势、均线、RSI 和 MACD 窗口与阈值 |
| `NAV_POSITION` | 历史窗口、样本下限和区域阈值 |
| `FACTOR` | 因子权重、标准化和缺失值策略 |
| `FUND_RISK` / `PORTFOLIO_RISK` | 风险窗口、样本下限、阈值和 VaR 语义 |
| `BACKTEST` | 按市场定义的执行费率与滑点，不含 D-010 未确认的胜率语义 |

每个配置组版本存储结构版本、规范化 JSON、SHA-256、状态和审计字段。`DRAFT` 可以编辑；`VALIDATED` 与 `PUBLISHED` 永久不可变。变更字段或结构语义时必须创建新草稿，禁止原地覆盖。

`ESTIMATE` schema v2 额外记录 `nav_decimal_scale` 与 `percentage_decimal_scale`。这是 Stage 3 的字段级序列化语义：NAV 使用 6 位小数，涨跌幅、权重、贡献与覆盖率使用 4 位小数；schema v1 保留其全局 6 位的历史含义。

## 结构演进

1. 新字段、规则或计算语义必须提高对应配置组的 `schema_version`，并先由 Java 和 Python 同时实现支持与校验。
2. 新结构版本必须保留旧结构版本的解析能力，直到所有仍在运行的任务和需要解释的历史结果均不再引用旧版本。
3. Java 在发布前调用 Python `POST /internal/v1/quant-config/validate`。Python 不支持该结构版本时返回 `QUANT_CONFIG_SCHEMA_UNSUPPORTED`，不得发布。
4. Java 与 Python 对 JSON 对象键按字典序规范化、保留数组顺序，并对规范化 UTF-8 JSON 计算 SHA-256。不得依赖格式化、字段输入顺序或源码默认值。

## 首次部署与发布

1. 按顺序执行 `fund-admin/script/sql/update/postgres/update_quant_config_center_v1.sql` 和 `fund-admin/script/sql/update/postgres/seed_quant_config_cross_market_v1.sql`。
2. 创建 `fund_quant_reader`，授予三个 `quant_config_*` 表以及 Stage 3 共享输入表 `fund_info`、`fund_nav`、`fund_holding` 的 `SELECT` 权限；设置只读事务、5 秒查询超时和 10 个连接上限，并把其 DSN 配置为 `FUND_QUANT_CONFIG_READONLY_DSN`。同一个共享连接池同时读取配置和估值输入，不得重复创建两个各自上限为 10 的池。
3. 种子只生成十个 `DRAFT` 版本。首版 `cross-market-fund-v1` 使用已确认的 D-011 参数。当前活动发布为 release v3，已于 `2026-08-08T16:57:38+08:00` 生效，复用已校验的十个配置版本与 release v2 的 checksum；历史发布记录不得修改。
4. 发布者在 Java 管理端逐组校验草稿，审查字段级 diff；Java 调用 Python 兼容性校验后才可提交发布。
5. Java 在一个事务中创建发布清单、分配单调递增的 `release_version`、计算发布校验和并提交。提交后刷新 Redis 投影并发布失效通知。

禁止直接插入或修改 `quant_config_release*` 表来发布、回滚或修复配置。这样会绕过校验、审计、幂等和 Java/Python 兼容性检查。

## API 与权限

| 权限 | 允许操作 |
| --- | --- |
| `fund:config:list` | 分组概览、版本列表、发布历史 |
| `fund:config:query` | 单个版本、diff、单个发布版本详情 |
| `fund:config:edit` | 创建、克隆和编辑 `DRAFT` |
| `fund:config:validate` | 校验草稿并转为 `VALIDATED` |
| `fund:config:publish` | 创建原子发布版本 |
| `fund:config:rollback` | 基于较早发布版本创建新的更高发布版本 |

所有编辑、校验、发布和回滚都保留 RuoYi 操作审计。发布与回滚具有重复提交保护；草稿编辑依赖 `revision` 乐观锁。

## 消费与错误处理

Java 在创建估值请求或异步量化任务时固定 `config_release_version` 与 `config_release_checksum`，并传入 Python 请求头 `X-Quant-Config-Release-Version`、`X-Quant-Config-Release-Checksum`。Python 仅加载该精确版本，响应和 Java 持久化的结果必须回显相同血缘。

| 错误码 | 含义 | 处理 |
| --- | --- | --- |
| `QUANT_CONFIG_NOT_PUBLISHED` | 指定发布版本不存在或尚未生效 | 停止计算；检查发布状态、生效时间与任务固定版本 |
| `QUANT_CONFIG_VERSION_MISMATCH` | 请求、发布清单、响应或持久化结果的版本不一致 | 停止计算并保留诊断信息；不得读取其他发布版本 |
| `QUANT_CONFIG_CHECKSUM_MISMATCH` | 发布、配置条目或调用方校验和不一致 | 停止计算；检查规范化 JSON 和发布清单，重新走校验/发布流程 |
| `QUANT_CONFIG_SCHEMA_UNSUPPORTED` | 任一端不支持配置组结构版本 | 升级两端实现或创建兼容发布版本，禁止降级到源码常量 |

对于量化配置错误，Java 不得用旧快照、当前活动版本、最新版本或 `sys_config` 作为替代。仅临时行情故障允许读取同一精确发布版本的陈旧估值快照。

## 故障恢复

### Redis 不可用或投影缺失

1. 不允许将 Redis 当作发布事实来源。
2. 从 PostgreSQL 的精确发布版本重建 `fund:quant-config:release:{releaseVersion}`、`fund:quant-config:group:{configCode}:{configVersion}` 与活动发布指针。
3. 验证发布校验和和所有组校验和后再恢复新任务；无法验证时保持严格失败。
4. 运行中的任务继续使用启动时固定的版本，不因活动指针变化而切换。

### PostgreSQL 不可用

1. Python 仅可使用以相同 `(release_version, checksum)` 键命中的不可变进程缓存；未命中或无法校验时必须失败。
2. 不得选择活动、最新或硬编码配置。恢复数据库后，清理失败任务并从其原始固定版本重新提交。
3. 发布或回滚事务失败时不移动活动投影指针；修复数据库后重新执行 Java 发布操作，而不是手动补写表数据。

## 回滚与历史重算

回滚不是修改或删除旧发布版本。发布者选择一个已验证的历史组合后，系统创建新的、更高的发布版本，并在 `rollback_of_release_version` 中记录来源。旧结果、旧快照与其校验和保持不变。

历史重算必须显式创建 SnailJob 任务，并固定目标发布版本、相关配置组版本、算法版本、数据版本、数据时间和计算时间。重算结果与原结果并存；禁止覆盖旧结果或将其标注为新配置产生。

估值历史重算使用 SnailJob 执行器 `fundEstimateRecalculationJob`，其参数严格为
`fundCode,releaseVersion,releaseChecksum`，例如 `000001,2,<64位发布校验和>`。执行器会重新读取该精确
发布版本并核验校验和；缺少、格式错误或不匹配的参数都会安全失败，绝不会替换为活动或最新发布版本。
