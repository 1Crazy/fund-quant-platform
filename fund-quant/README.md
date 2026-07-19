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
```

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
    "estimateTime": "2026-07-19T10:30:00+08:00",
    "source": "AKSHARE_HOLDING_ESTIMATE",
    "holdingCoverageRate": 14.7,
    "reportPeriod": "2026年2季度",
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

然后从 `fund-admin/ruoyi-admin` 启动 Java。Java 会解析 `success/data/error/requestId` 包装，校验估值结果，再写 Redis 和 `fund_estimate` 快照。

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

基金公告通常只披露前十大股票持仓，因此响应额外返回 `holdingCoverageRate`。第一阶段未覆盖债券、现金、港股、期货、申赎费用和基金经理盘中调仓，结果属于基于公开持仓的估算，不是基金公司官方净值。

## 测试

```bash
pytest
```

测试使用确定性的领域对象验证公式和字段标准化，不使用模拟行情替代生产数据；生产 Repository 始终调用真实 AkShare 接口。
