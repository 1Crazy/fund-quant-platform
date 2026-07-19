---
name: java-development-guidelines
description: "本项目 Java 开发规范、Spring Boot/MyBatis/PostgreSQL 排错与接口联调经验。用于新增或修改 fund-admin Java 功能，或诊断接口 500、数据库迁移、租户过滤、Mapper SQL、Python 供应方调用失败。"
---

# Java 开发规范

面向 `fund-admin` 的 Spring Boot、MyBatis、PostgreSQL 与 fund-quant 联调。遵循现有 RuoYi-Vue-Plus 模式，不引入新的迁移或 ORM 框架。

## 开发约定

- 新增跨租户公共基金数据表时，同时将表名加入 `fund-admin/ruoyi-admin/src/main/resources/application.yml` 的 `tenant.excludes`。
- 数据库结构变更写入 `fund-admin/script/sql/update/postgres/` 的正向迁移 SQL；本项目不自动执行 Flyway/Liquibase 迁移。
- 修改 Mapper XML 后，确认运行时使用的资源不是旧的 `target/classes` 或 `~/.m2` JAR；重新构建并重启 Java 服务后再判断结果。
- PostgreSQL 参数必须有明确类型上下文。避免 `CONCAT(#{value}, ...)` 这类可能让 JDBC 参数推断失败的写法；优先使用 `#{value}::varchar || '...'`。
- 本机 Java 调 Python 服务时，默认使用 `127.0.0.1` 而不是 `localhost`，避免 Python 仅监听 IPv4 时的 IPv6 解析差异。

## 接口 500 排查顺序

1. 读取 `fund-admin/ruoyi-admin/logs/sys-error.log` 中与请求时间匹配的首个 `Caused by`，不要以接口通用文案作为根因。
2. 对数据库错误，先核对报错列/表是否已由对应 PostgreSQL 迁移创建，再检查租户插件是否额外注入了 `tenant_id` 条件。
3. 对 Mapper 错误，记录最终 SQL、参数位置与运行时 XML 来源；若来源为 `~/.m2/...jar`，先排除构建产物陈旧。
4. 对 Java 调用 fund-quant 的错误，直接请求对应 Python 内部端点，区分连接失败、超时、非 2xx 和结构化业务错误。
5. Python 端点成功但 Java 失败时，检查 Java 的供应方基础地址、连接/读取超时、JSON DTO 字段和当前运行资源版本。

## 本项目已验证经验

- `fund_info` 与 `fund_nav` 新增版本/质量字段后，必须执行 `update_fund_data_center_v1.sql`；仅重启服务不会更新 PostgreSQL 结构。
- `fund_sync_run`、`fund_data_quality_issue` 与基金行情数据一样是跨租户公共数据；遗漏租户排除会触发 `column ... tenant_id does not exist`。
- 查询质量问题的前缀匹配应写成 `record_key LIKE (#{fundCode}::varchar || ':%')`，避免 PostgreSQL 报 `could not determine data type of parameter`。
- `/internal/v1/data/sync/fund/{code}`、`/sync/nav/{code}`、`/sync/holdings/{code}` 是 Java 手动同步的逐段诊断入口；它们只读取公开数据源，不能替代 Java 侧持久化验证。
- `RedisUtils.rateLimiter` 的最后一个参数会传给 Redisson 作为限流器键保留时间，并非获取许可等待时间；它必须不小于 `rateInterval`，否则会报 `keepAliveTime should be greater than or equal to rateInterval`。
