# fund-quant

`fund-quant` 是基金量化决策系统的内部数据与计算服务。第一阶段通过 AkShare 获取真实基金和 A 股数据，计算基金盘中估值，并为 Spring Boot 提供稳定的内部 API。

## 职责边界

- Python：AkShare/公开来源数据采集、字段标准化、Redis 数据缓存、估值计算；必要时只能通过只读角色读取跨租户共享基金数据集。
- Java：鉴权、面向前端的业务 API、私有范围参数传递、PostgreSQL 唯一写入口、Redis 业务缓存、估值快照落库和失败降级。
- Python 不写 RuoYi PostgreSQL，不读取用户、租户、组合等私有业务表，避免两个服务共同写业务表。

## 目录结构

```text
fund-quant/
├── app/
│   ├── api/
│   │   ├── dependencies.py
│   │   └── v1/data.py
│   ├── calculators/estimate_calculator.py
│   ├── clients/akshare_client.py
│   ├── core/
│   │   ├── cache.py
│   │   ├── config.py
│   │   └── exceptions.py
│   ├── repositories/
│   │   ├── fund_repository.py
│   │   └── stock_repository.py
│   ├── schemas/
│   │   ├── common.py
│   │   ├── estimate.py
│   │   └── market.py
│   ├── services/
│   │   ├── estimate_service.py
│   │   └── fund_data_service.py
│   └── main.py
├── tests/
├── .env.example
├── Dockerfile
├── docker-compose.yml
├── pyproject.toml
├── requirements-dev.txt
└── requirements.txt
```

## 首次启动

项目根目录已通过 `.python-version` 固定 Python 3.12.4。首次安装只需：

```bash
cd /Users/hong/Documents/my-project/jj/fund-quant
make install
```

使用本机无密码 Redis，默认连接 `localhost:6379` 的 DB 1。安装完成后启动：

```bash
make dev
```

本机服务以 IPv6 双栈监听，调用方统一使用 `http://localhost:8000`，不要写 `127.0.0.1`。

接口文档：`http://localhost:8000/docs`

## 日常开发启动

首次安装完成后，每天开发只需要：

```bash
cd /Users/hong/Documents/my-project/jj/fund-quant
make dev
```

如果已经在 `fund-quant` 目录，就只有一条命令：

```bash
make dev
```

`make dev` 会自动使用 `.venv` 中的 Uvicorn，不需要执行 `source .venv/bin/activate`。

完整的首次安装、日常启动和联调说明见：[Python 量化服务启动手册](../doc/getting-started/python-startup.md)。

## Docker 启动

Compose 会同时启动独立 Redis，并把宿主机 `6380` 映射到 Redis；容器内服务使用 `redis:6379`，不会占用本机已有的 `6379`。

```bash
docker compose up -d --build
```

## 内部接口

### 实时估值

```http
GET /internal/v1/data/estimate/000001
X-Quant-Config-Release-Version: <release-version>
X-Quant-Config-Release-Checksum: <sha256-checksum>
X-Fund-Estimate-Result-Cache-Seconds: <1-300>
X-Fund-Estimate-Quote-Cache-Seconds: <1-300>
```

估值请求必须同时携带已发布量化配置的精确版本和校验和。Java 在创建请求或任务时固定这两个值；Python 只加载该版本，不会回退到当前活动版本、最新版本或源码默认值。估值响应和持久化快照都保留相同的配置血缘。

### 股票实时行情

```http
GET /internal/v1/data/stock/600519
```

返回最新价格、涨跌幅、成交量和行情获取时间。估值引擎内部会批量读取全市场行情并通过 Redis 复用，不会为每只持仓股票重复请求 AkShare。

成功响应采用 Java 已接入的统一包装：

```json
{
  "success": true,
  "data": {
    "fundCode": "000001",
    "estimateNav": 1.235333,
    "estimateGrowthRate": 0.108,
    "previousNav": 1.234,
    "previousNavDate": "2026-07-18",
    "estimateTime": "2026-07-19T02:30:00Z",
    "source": "FUND_DATA_CENTER_HOLDING_ESTIMATE",
    "holdingCoverageRate": 71.7,
    "reportPeriod": "2026年2季度",
    "configReleaseVersion": 1,
    "configReleaseChecksum": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
    "contributions": []
  },
  "error": null,
  "requestId": "..."
}
```

### 历史净值

```http
GET /internal/v1/data/nav/000001?days=120
```

### 最新公开持仓

```http
GET /internal/v1/data/holdings/000001
```

### 基金基础信息

```http
GET /internal/v1/data/fund/000001
```

基金名称或拼音缩写搜索：

```http
GET /internal/v1/data/funds?keyword=有色&limit=50
```

基金档案接口会返回基金经理、托管人、成立日期、最新规模、基金评级和业绩比较基准。历史净值接口同时返回单位净值、累计净值和日增长率；`days=250` 表示最近 250 个净值公布日，不是最近 250 个自然日，`days=0` 表示成立以来全部历史。

### 量化配置兼容性复核

完整的配置目录、结构演进、发布/回滚、权限、稳定错误码和故障恢复说明见 [量化配置中心运行手册](../doc/quant-config-center-runbook.md)。

Java 在创建发布版本前调用下列内部接口，提交十个完整配置组及其精确的配置版本、规范化 JSON 校验和和发布校验和：

```http
POST /internal/v1/quant-config/validate
```

该接口只校验 Python 端是否支持所请求的结构版本，绝不写入 PostgreSQL。计算服务通过 `FUND_QUANT_CONFIG_READONLY_DSN` 使用只读 PostgreSQL 角色按 `(release_version, checksum)` 加载精确版本，并在同一只读连接池读取估值所需的共享 NAV 与披露持仓；不存在、版本不一致、校验和不一致或结构不支持时分别返回稳定的 `QUANT_CONFIG_*` 错误，绝不选择最新版本或源码默认值。

部署时先创建 `fund_quant_reader` 角色，再依序执行 `fund-admin/script/sql/update/postgres/update_quant_config_center_v1.sql`、`fund-admin/script/sql/update/postgres/seed_quant_config_cross_market_v1.sql` 与 `fund-admin/script/sql/update/postgres/update_harden_realtime_fund_estimation_v1.sql`。迁移授予量化配置表和共享数据中心输入表的只读权限，并设置默认只读事务、5 秒查询超时和 10 个连接上限。

首版 `cross-market-fund-v1` 的 D-011 参数已经确认，种子只写入十个不可自动发布的 `DRAFT` 配置版本。持有 `fund:config:publish` 权限的操作员必须先在 Java 管理端校验草稿，再让 Java 调用 Python 兼容性接口，最后创建原子发布版本。当前活动发布为立即生效的 release v3（`2026-08-08T16:57:38+08:00`），复用 release v2 的十个已校验配置版本及 checksum；发布会生成版本化校验和、审计记录和 Redis 投影。不得直接插入 `quant_config_release*` 表。

### 健康检查

```http
GET /health
```

Redis 不可用时健康状态为 `DEGRADED`，业务请求会跳过缓存继续访问 AkShare。

## Java 联调

RuoYi 的 `dev` 配置已经默认指向本机 `8000`，正常情况下启动 Python 后直接启动 Java 即可。地址变化时可在启动 RuoYi 前覆盖：

```bash
export FUND_ESTIMATE_PROVIDER_URL='http://localhost:8000/internal/v1/data/estimate/{code}'
```

然后从 `fund-admin/ruoyi-admin` 启动 Java。Java 会解析 `success/data/error/requestId` 包装，并校验请求固定的发布版本和校验和与响应完全一致，再写入同一配置血缘的 Redis 和 `fund_estimate` 快照。

Java 上游读取超时默认设置为 30 秒，用于覆盖 AkShare 冷缓存的首次加载；Python 缓存命中后通常无需等待完整超时。可通过 `fund.estimate.provider-read-timeout` 调整。

前端精确输入六位基金代码后仍只调用 Java `GET /fund/list`。Java 在本地不存在该基金时自动调用本服务的基金和净值接口，并幂等写入 `fund_info`、`fund_nav`，随后从 PostgreSQL 返回分页结果。

### 数据中心同步供应方接口

这些接口供 Java 同步编排调用，仍使用统一 `success/data/error/requestId` 包装。`data` 为 `SyncEnvelope`，包含 `meta`、`records` 和 `issues`；`meta` 固定携带 `batchId`、`dataset`、`source`、`sourceTime`、`fetchedAt`、`qualityStatus`、`checksum`、`dataVersion`、成功/拒绝/失败计数及分页游标。

```http
POST /internal/v1/data/sync/catalog
Content-Type: application/json

{"page":1,"pageSize":200,"batchId":"..."}
```

```http
POST /internal/v1/data/sync/fund/000001?batchId=...
POST /internal/v1/data/sync/nav/000001?startDate=2026-01-01&endDate=2026-06-30&batchId=...
POST /internal/v1/data/sync/holdings/000001?reportDate=2026-06-30&batchId=...
```

`/sync/fund/{code}`、`/sync/nav/{code}` 和 `/sync/holdings/{code}` 可额外接收可选 JSON body：

```json
{
  "batchId": "...",
  "requestedBy": "java-sync",
  "sharedContext": [
    {
      "fundCode": "000001",
      "latestDataVersion": "fund_nav:...",
      "latestNavDate": "2026-06-30",
      "latestHoldingReportDate": "2026-06-30",
      "qualityStatus": "NORMAL"
    }
  ]
}
```

该 body 只承载 Java 显式传入的共享基金上下文，不允许包含用户、租户或组合私有表数据。

## 估值口径

```text
估值涨跌幅（%） = Σ(持仓权重（%） × 股票涨跌幅（%） / 100)
估算净值 = 最近单位净值 × (1 + 估值涨跌幅（%） / 100)
```

基金公告通常只披露前十大股票持仓，因此响应额外返回 `holdingCoverageRate`。D-011 首版要求公开股票持仓覆盖率至少为 60%、行情年龄不超过 90 秒；存储时间遵循 UTC，接口时间以 `Asia/Shanghai` 的显式偏移传输。当前实现未覆盖债券、现金、港股、期货、申赎费用和基金经理盘中调仓，结果属于基于公开持仓的估算，不是基金公司官方净值。完整上线、灰度、故障与指标说明见 [盘中基金估值运行手册](../doc/realtime-fund-estimation-runbook.md)。

## 测试

```bash
pytest
```

测试使用确定性的领域对象验证公式和字段标准化，不使用模拟行情替代生产数据；生产 Repository 始终调用真实 AkShare 接口。
