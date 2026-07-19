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
source "$HOME/.sdkman/bin/sdkman-init.sh"
mvn install -Pdev -DskipTests
```

日常启动后端：

```bash
cd fund-admin/ruoyi-admin
mvn spring-boot:run
```

如果当前已经在 `fund-admin` 目录，只需：

```bash
cd ruoyi-admin
mvn spring-boot:run
```

`source` 仅在终端没有自动初始化 SDKMAN 时需要；只要 `java -version` 显示 JDK 21，日常无需执行。后端依赖安装只需在首次启动或 Maven 配置发生变化后执行。

后端地址：`http://localhost:8080`

前端启动：

```bash
cd /Users/hong/Documents/my-project/jj/fund-web
pnpm dev:admin
```

前端地址：`http://localhost:5777`

## 文档入口

- [后端启动手册](./doc/getting-started/backend-startup.md)
- [启动手册目录](./doc/getting-started/README.md)
- [基金量化决策系统 V1 设计](./doc/fund-quant-decision-v1-design.md)
- [基金实时估值 DDL](./fund-admin/script/sql/postgres/postgres_fund_realtime.sql)
- [基金量化 V1 完整 DDL](./fund-admin/script/sql/postgres/postgres_fund_quant_v1.sql)

## 环境约定

- Java 21：使用 SDKMAN 管理。
- Python 3.12：使用 pyenv 管理。
- PostgreSQL 17：默认数据库 `fund_quant`。
- Redis 8：开发环境默认地址 `localhost:6379`，无密码。

## 当前模块

- 基金实时估值：`/fund/list`、`/fund/detail`
- 基金详情与净值走势：`/fund/detail?code=<基金代码>`
- 后端接口：`/fund/list`、`/fund/detail/{code}`、`/fund/estimate/{code}`

量化结果仅供辅助决策，不构成投资建议。
