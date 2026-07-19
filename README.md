# 基金量化决策系统

基金量化决策系统 V1，基于现有 RuoYi-Vue-Plus、Vben Admin 和 Python 量化服务持续开发。

## 项目结构

```text
fund-admin/   Spring Boot + RuoYi-Vue-Plus 后端
fund-web/     Vben Admin + Vue 3 前端
fund-quant/   Python + FastAPI 量化服务
doc/          架构设计、DDL 与开发手册
```

## 快速启动

首次安装后端 Maven 依赖：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin
make install
```

日常启动后端：

```bash
cd fund-admin
make dev
```

如果当前已经在 `fund-admin` 目录，只需：

```bash
make dev
```

`make dev` 会自动使用 SDKMAN 当前的 JDK 与 Maven，先从聚合工程增量安装当前模块，再启动 `ruoyi-admin`，避免子模块修改后仍加载 `~/.m2` 中旧 Jar。不需要手动执行 `source`。后端依赖安装只需在首次启动或 Maven 配置发生变化后执行。

后端地址：`http://localhost:8080`

前端启动：

```bash
cd /Users/hong/Documents/my-project/jj/fund-web
pnpm dev:admin
```

前端地址：`http://localhost:5777`

量化服务首次安装并启动：

```bash
cd /Users/hong/Documents/my-project/jj/fund-quant
make install
make dev
```

量化服务日常启动：

```bash
cd /Users/hong/Documents/my-project/jj/fund-quant
make dev
```

量化服务地址：`http://localhost:8000`。Java 的 `dev` 配置默认调用该地址，无需每次设置环境变量；地址变化时再覆盖：

```bash
export FUND_DATA_PROVIDER_BASE_URL='http://localhost:8000'
export FUND_ESTIMATE_PROVIDER_URL='http://localhost:8000/internal/v1/data/estimate/{code}'
```

精确输入六位基金代码查询时，Java 会自动从量化服务同步基金基础信息和最新净值；进入详情后再按近1月、近3月、近6月、近1年、近3年、近5年或成立以来补齐 PostgreSQL 数据与最新公开股票持仓，前端无需额外执行导入。

## 文档入口

- [后端启动手册](./doc/getting-started/backend-startup.md)
- [Python 量化服务启动手册](./doc/getting-started/python-startup.md)
- [启动手册目录](./doc/getting-started/README.md)
- [基金量化决策系统 V1 设计](./doc/fund-quant-decision-v1-design.md)
- [基金实时估值 DDL](./fund-admin/script/sql/postgres/postgres_fund_realtime.sql)
- [基金量化 V1 完整 DDL](./fund-admin/script/sql/postgres/postgres_fund_quant_v1.sql)
- [Python 量化服务手册](./fund-quant/README.md)

## 环境约定

- Java 21：使用 SDKMAN 管理。
- Python 3.12：使用 pyenv 管理。
- PostgreSQL 17：默认数据库 `fund_quant`。
- Redis 8：开发环境默认地址 `localhost:6379`，无密码。

## 当前模块

- 基金实时估值：`/fund/list`、`/fund/detail`
- 基金详情与净值走势：`/fund/detail?code=<基金代码>`
- 后端接口：`/fund/list`、`/fund/detail/{code}?period=3m`、`/fund/estimate/{code}`

量化结果仅供辅助决策，不构成投资建议。
