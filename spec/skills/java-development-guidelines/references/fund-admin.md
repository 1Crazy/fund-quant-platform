# fund-admin 项目参考

仅在修改 `fund-admin/` 下的 Java、Mapper XML、PostgreSQL 迁移或 `fund-quant` 调用时加载本文件。这里记录的是该项目已验证的环境与排障经验；涉及 BO/VO、统一响应或分页时，同时读取 [ruoyi-vue-plus.md](ruoyi-vue-plus.md)。通用编码约束见上级 [`SKILL.md`](../SKILL.md)。

## 使用边界

- 当前仓库中的代码、配置、迁移和运行日志是事实来源；本文件与它们冲突时，以当前事实为准，并更新或记录已过期的经验。
- 本文件提供排查顺序，不授权执行构建、启动、重启、请求内部服务或修改环境。仅在用户明确授权相应验证后执行这些操作。
- 新增或更新项目经验时，记录可追溯来源（代码/配置/迁移路径、问题单或验证日期），避免把一次性现象固化为长期约定。

## 项目约定

- 本项目使用 RuoYi-Vue-Plus、Spring Boot、MyBatis、PostgreSQL；沿用现有结构，不引入新的迁移或 ORM 框架。
- 新增跨租户公共基金数据表时，同时将表名加入 `fund-admin/ruoyi-admin/src/main/resources/application.yml` 的 `tenant.excludes`。
- 数据库结构变更写入 `fund-admin/script/sql/update/postgres/` 的正向迁移 SQL；项目不会自动执行 Flyway/Liquibase 迁移。
- 修改 Mapper XML 后，确认运行时使用的资源不是旧的 `target/classes` 或 `~/.m2` JAR；仅在获得构建和启动验证授权后，重新构建并重启 Java 服务再判断结果。
- PostgreSQL 参数必须有明确类型上下文。避免 `CONCAT(#{value}, ...)` 让 JDBC 参数推断失败；优先写 `#{value}::varchar || '...'`。
- 本机 Java 调 Python 服务默认使用 `127.0.0.1`，不用 `localhost`，以避免 Python 仅监听 IPv4 时的 IPv6 解析差异。
- 新增 Controller 接口先检查相邻方法的 `@SaCheckPermission` 权限标识；不能遗漏操作权限，不能以请求参数中的 `userId`、`roleId`、`tenantId` 替代后端上下文判断。
- 数据范围由项目已有数据权限能力在查询阶段处理，禁止 Service 查询全量数据后再用 Java 过滤。多租户表依赖 `PlusTenantLineHandler`；只有跨租户公共表才按现有约定加入 `tenant.excludes`。

## 接口 500 排查顺序

1. 读取 `fund-admin/ruoyi-admin/logs/sys-error.log` 中与请求时间匹配的首个 `Caused by`，不要以接口通用文案作为根因。
2. 对数据库错误，先核对报错列/表是否已由对应 PostgreSQL 迁移创建，再检查租户插件是否额外注入了 `tenant_id` 条件。
3. 对 Mapper 错误，记录最终 SQL、参数位置与运行时 XML 来源；若来源为 `~/.m2/...jar`，先排除构建产物陈旧。
4. 对 Java 调用 `fund-quant` 的错误，直接请求对应 Python 内部端点，区分连接失败、超时、非 2xx 和结构化业务错误。
5. Python 端点成功但 Java 失败时，检查 Java 的供应方基础地址、连接/读取超时、JSON DTO 字段和当前运行资源版本。

## 已验证经验

- `fund_info` 与 `fund_nav` 新增版本/质量字段后，必须执行 `update_fund_data_center_v1.sql`；仅重启服务不会更新 PostgreSQL 结构。来源：`fund-admin/script/sql/update/postgres/update_fund_data_center_v1.sql:16-30`；核验：2026-08-02。
- `fund_sync_run`、`fund_data_quality_issue` 与基金行情数据一样是跨租户公共数据；遗漏租户排除会触发 `column ... tenant_id does not exist`。来源：`fund-admin/ruoyi-admin/src/main/resources/application.yml:136-146`；核验：2026-08-02。
- 查询质量问题的前缀匹配应写成 `record_key LIKE (#{fundCode}::varchar || ':%')`，避免 PostgreSQL 报 `could not determine data type of parameter`。来源：`fund-admin/ruoyi-modules/ruoyi-fund/src/main/resources/mapper/fund/FundDataQualityIssueMapper.xml:90-94`；核验：2026-08-02。
- `/internal/v1/data/sync/fund/{code}`、`/internal/v1/data/sync/nav/{code}`、`/internal/v1/data/sync/holdings/{code}` 是 Java Provider Client 的逐段同步入口；Provider 响应成功不能替代 Java 侧持久化验证。来源：`fund-admin/ruoyi-modules/ruoyi-fund/src/main/java/org/dromara/fund/client/FundDataProviderClient.java:106-165`；核验：2026-08-02。
- `RedisUtils.rateLimiter` 的最后一个参数会被转换为 Redisson `trySetRate` 的第四个 `Duration` 参数，而本项目随后调用的是无参 `tryAcquire()`；它不是获取许可的等待时间。当前 Redisson 3.52.0 部署下，该值需不小于 `rateInterval`，否则会报 `keepAliveTime should be greater than or equal to rateInterval`。来源：`fund-admin/ruoyi-common/ruoyi-common-redis/src/main/java/org/dromara/common/redis/utils/RedisUtils.java:53-56`、`fund-admin/pom.xml:32`；核验：2026-08-02。
