# 基金量化决策系统 V1 详细设计

> 适用仓库：`fund-web`、`fund-admin`、`fund-quant`  
> 设计目标：在现有 Vben Admin 5.7.0 + RuoYi-Vue-Plus 5.6.2 基础上增加基金业务，不改造基础框架。  
> V1 范围：基金实时估值、120 日趋势、买卖点信号、市场温度计、组合风险分析。

## 0. 现状与实施约束

仓库检查结果：

- 前端实际为 Vben Admin 5.7.0 Element Plus 应用，应用目录已由 `apps/web-ele` 改为 `apps/admin`。
- 后端实际为 RuoYi-Vue-Plus 5.6.2，当前 `pom.xml` 使用 Spring Boot 3.5.15、MyBatis-Plus 3.5.16。
- 后端当前 `java.version` 是 17，数据源默认是 MySQL；实施第一步需要切到 JDK 21 与 PostgreSQL。
- `fund-quant` 当前为空目录，V1 在该目录内新增独立 FastAPI 服务。
- Vben 当前请求客户端把 `code=0` 作为成功；RuoYi 的 `R` 与 `TableDataInfo` 使用 `code=200`，必须先做响应适配。
- Vben 开发代理当前仍指向 mock 服务 `http://localhost:5320/api`，联调时改为 RuoYi 地址，例如 `http://localhost:8080`。
- RuoYi 已有 Redisson、`RedisUtils`、Spring Cache、分页、权限、日志和多租户能力，基金模块直接复用。
- RuoYi 当前开启多租户。基金行情、净值、估值、趋势、信号、市场指标是共享数据，应加入 `tenant.excludes`；基金组合和组合风险必须保留 `tenant_id`。

V1 明确不做：自动下单、券商账户接入、盘中交易撮合、收益承诺、模型训练、分钟级回测。页面必须展示“量化结果仅供辅助决策，不构成投资建议”。

---

## 第一部分：完整系统架构设计

### 1.1 逻辑架构

```text
浏览器
  |
  | HTTPS / JSON / Bearer Token
  v
Vben Admin（fund-web/apps/admin）
  - 路由、页面、Pinia 查询状态
  - Vben Form / VXE Grid / ECharts
  |
  | /fund/**、/market/**、/portfolio/**
  v
RuoYi-Vue-Plus（fund-admin/ruoyi-admin）
  - Sa-Token 鉴权、租户、权限、审计、参数校验
  - 基金业务编排、数据库唯一写入口、Redis Cache Aside
  - 定时同步与计算任务
  |                         |
  | MyBatis-Plus            | HTTP /internal/v1/**
  v                         v
PostgreSQL               FastAPI（fund-quant）
  - 主数据/历史净值         - AkShare 数据适配与字段标准化
  - 指标/信号快照           - Pandas/Numpy/TA-Lib 指标计算
  - 持仓/组合/风险           - 趋势、信号、温度、组合风险
  ^                         - 无业务数据库写权限
  |
Redis
  - 估值、详情、指标、温度、组合风险缓存
  - 防击穿分布式锁
```

### 1.2 服务职责边界

| 层 | 负责 | 不负责 |
|---|---|---|
| Vben | 筛选、展示、图表、用户组合输入、错误/过期状态提示 | 指标计算、规则判定、直接调用 AkShare |
| Spring Boot | 公网 API、鉴权、权限、校验、事务、缓存、持久化、调用 Python、降级 | 用 Java 重复实现量化公式 |
| FastAPI | AkShare 访问与标准化、TA-Lib/Pandas 计算、算法版本化 | 用户鉴权、RuoYi 菜单、直接修改业务表 |
| PostgreSQL | 可追溯历史、快照、组合数据 | 高频实时缓存 |
| Redis | 热点读取、防击穿、短时降级 | 唯一事实来源、永久历史存储 |

### 1.3 关键调用流程

#### 基金列表

1. Vben 请求 `GET /fund/list?pageNum=1&pageSize=20&fundName=&fundType=`。
2. 基金代码、名称、类型、来源、质量状态、同步状态和历史位置筛选均直接查询已同步到 PostgreSQL 的本地数据，不在列表读请求中触发上游同步。
3. Spring 从 `fund_info` 分页查询，并批量读取每只基金最新净值与 Redis 最新估值；进入详情后再按近1月、近3月、近6月、近1年、近3年、近5年或成立以来按需补齐净值。
4. 返回 RuoYi `TableDataInfo`：`{ code, msg, rows, total }`。
5. 前端 VXE Grid 将 `rows -> items` 适配到现有表格协议，禁止逐行再次请求形成 N+1。

#### 实时估值

1. Spring 查询 `fund:estimate:{code}`。
2. 命中即返回；未命中则尝试 Redisson 锁 `fund:lock:estimate:{code}`，锁等待不超过 800ms。
3. 获锁后调用 Python `GET /internal/v1/data/estimate/{code}`。
4. Spring 校验代码、时间、数值范围后写 Redis；每 5 分钟或交易日最后一次写 `fund_estimate`。
5. Python/AkShare 异常时返回最近缓存或数据库快照，并设置 `isStale=true`、`sourceStatus=STALE`，不得伪装成实时数据。

#### 趋势与信号

1. Spring 从 `fund_nav` 取截至最近净值日的 180 条数据，至少要求 120 条有效净值。
2. Spring POST 到 Python `/internal/v1/analysis/trend`；Python 返回指标、得分、算法版本。
3. Spring upsert `fund_trend_snapshot` 并缓存 6 小时。
4. 信号接口复用同一份趋势结果，调用 `/internal/v1/analysis/signal`，upsert `fund_signal_snapshot`。
5. 同一 `fundCode + tradeDate + algorithmVersion` 幂等，不重复插入。

#### 市场温度

1. 定时任务在交易日 09:35、11:35、15:10 执行；页面请求只读取最近快照，缓存过期时可触发一次受锁保护的刷新。
2. Python 采集三个指数 PB、北向资金和股债收益差所需数据，统一返回原始值、百分位、分项得分、来源时间。
3. Spring 保存 `market_temperature` 与 `market_temperature_item`，Redis TTL 10 分钟。

#### 组合风险

1. 前端提交组合 ID 或一次性基金权重；保存组合时要求权重合计为 100%，允许误差 `0.01`。
2. Spring 校验租户数据权限，加载基金净值、最新持仓、行业配置并构造 Python 请求。
3. Python 计算重合度、行业集中度、组合波动率、最大回撤、风险分。
4. Spring 保存 `portfolio_risk_snapshot`，按“组合 ID + 持仓更新时间 + 算法版本”缓存 30 分钟。

### 1.4 缓存设计

| Key | Value | TTL | 失效/刷新 |
|---|---|---:|---|
| `fund:info:{code}` | 基金详情 | 30 分钟 | 基础信息同步后删除 |
| `fund:nav:{code}:120` | 走势图点位 | 1 小时 | 净值同步后删除 |
| `fund:estimate:{code}` | 最新估值 | 45 秒 | 主动刷新；非交易时段可延长至 30 分钟 |
| `fund:trend:{code}:{tradeDate}:{version}` | 趋势结果 | 6 小时 | 新净值或算法版本变化 |
| `fund:signal:{code}:{tradeDate}:{version}` | 信号结果 | 6 小时 | 趋势结果更新后删除 |
| `market:temperature:latest:{version}` | 温度及分项 | 10 分钟 | 定时计算后覆盖 |
| `portfolio:risk:{tenantId}:{portfolioId}:{fingerprint}` | 风险结果 | 30 分钟 | 组合、持仓或算法变化 |

所有缓存统一采用 Cache Aside。缓存内容包含 `dataTime`、`calculatedAt`、`isStale`，页面据此展示数据新鲜度。空基金代码使用 60 秒空值缓存，防止穿透。

### 1.5 定时任务

复用 `ruoyi-job`，新增任务调用 `IFundSyncService`：

| Job | 建议 cron | 行为 |
|---|---|---|
| `syncFundInfoJob` | 每日 02:10 | 拉取基金基础信息并 upsert |
| `syncFundNavJob` | 交易日 18:30 | 增量同步开放式基金净值 |
| `syncFundHoldingJob` | 每日 03:20 | 同步最新披露季报持仓、行业配置 |
| `refreshFundEstimateJob` | 交易日 09:30-15:00 每 5 分钟 | 仅刷新关注/组合内基金，控制第三方压力 |
| `calculateTrendSignalJob` | 交易日 19:00 | 为有新净值的基金计算趋势和信号 |
| `calculateMarketTemperatureJob` | 交易日 09:35、11:35、15:10 | 生成温度快照 |

任务使用 Redisson 分布式锁，单基金失败不回滚整批；保存成功代码、失败代码、错误摘要和耗时到现有任务日志。

### 1.6 算法口径

#### 120 日趋势

- 输入：按净值日期升序的复权单位净值，去重、去空、要求至少 120 个有效样本。
- MA：TA-Lib `SMA(close, 5/10/20/60/120)`。
- RSI：TA-Lib `RSI(close, 14)`，Wilder 口径。
- MACD：TA-Lib `MACD(close, fastperiod=12, slowperiod=26, signalperiod=9)`；返回 DIF、DEA、HIST，金叉使用前后两个交易日判断。
- 最大回撤：`abs(min(nav / cummax(nav) - 1)) * 100`。
- 年化波动率：`std(pct_change(nav), ddof=1) * sqrt(250) * 100`。

趋势分数固定为 0~100：

| 维度 | 满分 | 得分规则 |
|---|---:|---|
| MA | 30 | `MA20>MA60` 15；`MA5>MA10>MA20` 10；最新净值高于 MA20 5 |
| MACD | 25 | `DIF>DEA` 15；HIST 连续两期上升 10 |
| RSI | 15 | `[45,65]` 得 15；`[35,45)` 或 `(65,75]` 得 8；其余 0 |
| 最大回撤 | 15 | `<=10%` 得 15；`<=20%` 得 10；`<=30%` 得 5；其余 0 |
| 波动率 | 15 | `<=15%` 得 15；`<=25%` 得 10；`<=35%` 得 5；其余 0 |

`score >= 70 -> bull`，`score <= 39 -> bear`，其他为 `neutral`。所有阈值集中在 `app/core/config.py`，算法版本初始为 `trend-v1.0.0`。

#### 买卖信号

- BUY：趋势分 `>=70`，`MA20>MA60`，当日 MACD 金叉，RSI 在 `[40,70]`，最大回撤 `<=25%`，波动率 `<=35%`。
- SELL：满足任一强退出条件：`MA20<MA60` 且 MACD 死叉；RSI `>=80` 且 HIST 下降；最大回撤 `>35%`；波动率 `>50%`。
- HOLD：其他情况或样本不足。样本不足的 `reasonCode` 为 `INSUFFICIENT_NAV_DATA`，不能输出 BUY。
- 信号得分复用趋势分；SELL 时返回风险反向分 `100 - trendScore`，同时在 `details.trendScore` 保留原趋势分，避免语义含混。
- `reason` 返回结构化原因，不只返回中文字符串，前端使用 `message` 展示、`code` 做筛选。

#### 市场温度

分数越低越冷、越低估，与示例 `score=25, level=低估` 一致：

- 沪深300 PB 历史百分位：15%。
- 中证500 PB 历史百分位：15%。
- 创业板 PB 历史百分位：15%。
- 北向资金 20 日标准分经 winsorize 后映射到 0~100：25%。
- 股债收益差：`1 / 沪深300 PE(TTM) * 100 - 中国10年期国债收益率`，其历史百分位反向计分：30%。
- 历史百分位默认使用最近 5 年数据；少于 750 个有效交易日则标记 `isStale=true` 并拒绝生成正式快照。
- 等级：0~20 极度低估，21~40 低估，41~60 合理，61~80 高估，81~100 极度高估。

AkShare 只出现在 `provider/akshare_provider.py`，上层不得依赖中文列名。Provider 启动时执行字段契约检查；字段漂移时返回 `DATA_PROVIDER_SCHEMA_CHANGED`，不允许用错列继续计算。

#### 组合风险

- 组合日收益：将各基金净值按共同交易日内连接，先前值填充最多 3 个自然日，然后按用户权重计算；有效共同样本至少 120 日。
- 两基金重仓股重合度：`sum(min(holdingWeightA[s], holdingWeightB[s]))`。
- 组合重合度：按基金组合权重乘积对所有基金对的重合度加权平均。
- 行业集中度：基金行业配置乘组合权重后聚合，取最大行业权重作为 `industryRate`；同时在详情返回前三行业。
- 组合波动率与最大回撤沿用趋势模块口径。
- 风险分：重合度 25%、行业集中度 25%、波动率 25%、最大回撤 25%，每项按阈值线性映射到 0~100；分越高风险越高。
- 风险等级：`0~20 A+`、`21~35 A`、`36~50 B+`、`51~65 B`、`66~80 C`、`81~100 D`。

### 1.7 非功能要求

- Spring -> Python：连接 1 秒、读取 8 秒；实时估值读取 3 秒；携带 `X-Request-Id` 与 `X-Internal-Token`。
- Python `/health/live` 不访问外部数据源，`/health/ready` 检查 TA-Lib 加载与配置，不在健康检查中高频请求 AkShare。
- 公共查询接口建议限流：估值单用户 60 次/分钟，趋势/信号 30 次/分钟，温度 60 次/分钟。
- 日志不得记录完整 Token；组合计算日志只记录组合 ID、基金数量、耗时，不记录用户金额。
- 金额/净值数据库使用 `numeric`，Java 使用 `BigDecimal`，Python 计算使用 `float64`，响应前统一舍入；前端禁止用字符串拼接参与计算。

---

## 第二部分：数据库 DDL

完整可执行 PostgreSQL DDL：

`fund-admin/script/sql/postgres/postgres_fund_quant_v1.sql`

### 2.1 表清单

| 表 | 用途 | 多租户 |
|---|---|---|
| `fund_info` | 基金主数据 | 共享，排除 |
| `fund_nav` | 历史净值 | 共享，排除 |
| `fund_estimate` | 盘中估值快照 | 共享，排除 |
| `fund_trend_snapshot` | 120 日趋势快照 | 共享，排除 |
| `fund_signal_snapshot` | 买卖信号与原因 | 共享，排除 |
| `market_temperature` | 市场温度主快照 | 共享，排除 |
| `market_temperature_item` | 温度分项与来源 | 共享，排除 |
| `fund_holding` | 基金重仓股 | 共享，排除 |
| `fund_industry_allocation` | 基金行业配置 | 共享，排除 |
| `fund_portfolio` | 用户组合 | 租户隔离 |
| `fund_portfolio_item` | 组合基金及权重 | 租户隔离 |
| `portfolio_risk_snapshot` | 组合风险快照 | 租户隔离 |

`application.yml` 的 `tenant.excludes` 追加前九张共享表，不得把后三张组合表加入排除列表。

### 2.2 数据保留

- `fund_nav` 永久保留。
- `fund_estimate` V1 保留 180 天，按月清理；若规模上升，V2 再做按月分区。
- 趋势/信号/市场温度保留算法版本，算法升级不覆盖旧版本。
- 持仓与行业数据按报告期永久保留，页面默认取每只基金最新共同报告期。
- 组合逻辑删除，风险快照随组合删除级联清理。

---

## 第三部分：RuoYi-Vue-Plus 模块划分

### 3.1 完整新增目录

```text
fund-admin/
├── pom.xml                                  # java.version 改 21
├── ruoyi-admin/
│   ├── pom.xml                              # 启用 PostgreSQL，依赖 ruoyi-fund
│   └── src/main/resources/
│       ├── application.yml                  # 共享表 tenant.excludes
│       ├── application-dev.yml              # PostgreSQL、Redis、quant.base-url
│       └── application-prod.yml
├── ruoyi-common/                            # 不改基础能力
├── ruoyi-modules/
│   ├── pom.xml                              # 新增 <module>ruoyi-fund</module>
│   └── ruoyi-fund/
│       ├── pom.xml
│       └── src/main/
│           ├── java/org/dromara/fund/
│           │   ├── controller/
│           │   │   ├── FundController.java
│           │   │   ├── MarketController.java
│           │   │   └── PortfolioController.java
│           │   ├── domain/
│           │   │   ├── FundInfo.java
│           │   │   ├── FundNav.java
│           │   │   ├── FundEstimate.java
│           │   │   ├── FundTrendSnapshot.java
│           │   │   ├── FundSignalSnapshot.java
│           │   │   ├── MarketTemperature.java
│           │   │   ├── MarketTemperatureItem.java
│           │   │   ├── FundHolding.java
│           │   │   ├── FundIndustryAllocation.java
│           │   │   ├── FundPortfolio.java
│           │   │   ├── FundPortfolioItem.java
│           │   │   └── PortfolioRiskSnapshot.java
│           │   ├── domain/bo/
│           │   │   ├── FundQueryBo.java
│           │   │   └── PortfolioRiskBo.java
│           │   ├── domain/dto/
│           │   │   ├── QuantTrendRequest.java
│           │   │   ├── QuantSignalRequest.java
│           │   │   └── QuantPortfolioRiskRequest.java
│           │   ├── domain/vo/
│           │   │   ├── FundListVo.java
│           │   │   ├── FundDetailVo.java
│           │   │   ├── FundEstimateVo.java
│           │   │   ├── FundTrendVo.java
│           │   │   ├── FundSignalVo.java
│           │   │   ├── MarketTemperatureVo.java
│           │   │   └── PortfolioRiskVo.java
│           │   ├── mapper/                  # 每张表一个 BaseMapperPlus
│           │   ├── service/
│           │   │   ├── IFundQueryService.java
│           │   │   ├── IFundEstimateService.java
│           │   │   ├── IFundAnalysisService.java
│           │   │   ├── IMarketTemperatureService.java
│           │   │   ├── IPortfolioRiskService.java
│           │   │   └── IFundSyncService.java
│           │   ├── service/impl/
│           │   ├── client/
│           │   │   ├── QuantServiceClient.java
│           │   │   └── QuantClientProperties.java
│           │   ├── config/QuantClientConfig.java
│           │   ├── constant/FundCacheConstants.java
│           │   ├── job/FundQuantJob.java
│           │   └── exception/QuantServiceException.java
│           └── resources/mapper/fund/
│               ├── FundInfoMapper.xml
│               ├── FundNavMapper.xml
│               └── PortfolioRiskMapper.xml
└── script/sql/postgres/
    └── postgres_fund_quant_v1.sql
```

### 3.2 模块依赖

`ruoyi-fund/pom.xml` 只依赖现有模块：

- `ruoyi-common-core`
- `ruoyi-common-web`
- `ruoyi-common-mybatis`
- `ruoyi-common-redis`
- `ruoyi-common-security`
- `ruoyi-common-log`
- `ruoyi-common-tenant`
- `ruoyi-common-json`

HTTP 客户端使用 Spring Framework 已提供的 `RestClient.Builder`，V1 不新增 Feign、Resilience4j 等依赖。`ruoyi-admin/pom.xml` 注释 MySQL 驱动并启用现有 PostgreSQL 驱动声明。

### 3.3 Controller 与权限

| Controller | 基础路径 | 方法 | 权限 |
|---|---|---|---|
| `FundController` | `/fund` | list/detail/estimate/trend/signal | `fund:info:list/query`、`fund:analysis:query` |
| `MarketController` | `/market` | temperature | `market:temperature:query` |
| `PortfolioController` | `/portfolio` | risk | `portfolio:risk:query` |

组合保存接口虽未在用户指定 V1 接口中，但风险页面必须能形成组合，建议一并提供 `GET/POST/PUT/DELETE /portfolio` 和 `/portfolio/{id}/items`；若严格只做风险计算，则 `/portfolio/risk` 接受临时基金权重且不保存组合。

### 3.4 RuoYi 实现规范

- Entity：共享表继承 `BaseEntity`；组合三表继承 `TenantEntity`。
- BO：查询参数使用 `@Validated(QueryGroup.class)`；组合权重用 Bean Validation 校验。
- VO：使用现有 `@AutoMapper` 映射，接口不直接返回 Entity。
- Mapper：单表查询优先 `BaseMapperPlus`；最新净值、列表聚合和组合风险使用 XML，避免 Java N+1。
- Service：事务只包数据库操作，调用 Python 不放在长事务内；先计算、再短事务 upsert。
- Controller：列表返回 `TableDataInfo<FundListVo>`，其余返回 `R<T>`；统一异常交给 RuoYi 全局异常处理。
- 权限：只读接口也必须使用 `@SaCheckPermission`；定时任务走内部 Service，不绕到公网 Controller。

---

## 第四部分：Vben Admin 页面划分

### 4.1 完整新增目录

```text
fund-web/apps/admin/src/
├── api/
│   ├── fund/
│   │   ├── model.ts
│   │   ├── info.ts
│   │   └── analysis.ts
│   ├── market/
│   │   ├── model.ts
│   │   └── temperature.ts
│   └── portfolio/
│       ├── model.ts
│       └── risk.ts
├── router/routes/modules/
│   ├── fund.ts
│   ├── market.ts
│   └── portfolio.ts
├── store/
│   ├── fund.ts                              # 选中基金、筛选条件；不复制服务端大列表
│   └── portfolio.ts                         # 临时组合编辑状态
├── views/
│   ├── fund/
│   │   ├── list/index.vue                   # /fund/list
│   │   ├── detail/index.vue                 # /fund/detail?code=000001
│   │   ├── trend/index.vue                  # /fund/trend?code=000001
│   │   ├── signal/index.vue                 # /fund/signal?code=000001
│   │   └── components/
│   │       ├── FundSelector.vue
│   │       ├── EstimateSummary.vue
│   │       ├── NavChart.vue
│   │       ├── TrendMetrics.vue
│   │       └── SignalReasons.vue
│   ├── market/temperature/index.vue         # /market/temperature
│   └── portfolio/risk/index.vue             # /portfolio/risk
└── locales/langs/zh-CN/
    ├── fund.json
    ├── market.json
    └── portfolio.json
```

### 4.2 页面职责

| 页面 | 主体 | 关键交互 | 空/错状态 |
|---|---|---|---|
| `/fund/list` | VXE Grid：代码、名称、类型、最新净值、估值、涨跌幅、更新时间 | 搜索、类型筛选、分页、点击进入详情 | 无数据、估值过期标签、单行估值失败不影响列表 |
| `/fund/detail` | 基金摘要、实时估值、净值折线、最新股票持仓 | code 路由查询、近1月/3月/6月/1年/3年/5年/成立以来切换、手动刷新估值 | code 不存在跳回列表；曲线或持仓不足时展示真实空态 |
| `/fund/trend` | 基金选择器、趋势分、MA/RSI/MACD、回撤/波动率 | 切换基金；图表展示 MA5/10/20/60/120 | 样本不足时不画误导性指标 |
| `/fund/signal` | BUY/SELL/HOLD、分数、结构化原因、指标证据 | 切换基金、跳转趋势页 | 信号过期与算法版本可见 |
| `/market/temperature` | 0~100 仪表盘、等级、5 个分项、历史折线 | 刷新、查看来源时间 | 任一核心指标过期时展示数据质量告警 |
| `/portfolio/risk` | 基金组合编辑表、风险评级、重合度、行业集中度、回撤/波动率 | 增删基金、设置权重、校验合计、分析 | 少于 2 只基金、权重不为 100%、样本不足分别提示 |

### 4.3 前端复用方式

- 页面容器用 `Page`；筛选用现有 `useVbenForm`；列表用 `useVbenVxeGrid`。
- 图表复用 `@vben/plugins/echarts` 的 `EchartsUI/useEcharts`，不单独安装 ECharts 包。
- 请求复用 `requestClient`，业务 API 不自行创建 Axios 实例。
- 代码、权重、日期、百分比格式化集中到 API model/formatter，页面不重复实现。
- 基金选择器做成共享组件，趋势页与信号页共用；详情、趋势、信号通过 query `code` 保持上下文。
- 路由权限继续使用现有 Vben guard；后端菜单的 `component` 对应 `fund/list/index` 等实际 view path。

### 4.4 首要适配项

1. `src/api/request.ts`：`successCode` 从 `0` 改为 `200`；分页接口保留完整响应，映射 `rows -> items`、`total -> total`。
2. `src/api/core/auth.ts`：登录参数适配 RuoYi 的 `tenantId/clientId/grantType/username/password`，响应字段适配 `access_token`。
3. `src/api/core/user.ts`：从 `/user/info` 改为 `/system/user/getInfo` 并映射为 Vben `UserInfo`。
4. `src/api/core/menu.ts`：从 `/menu/all` 改为 `/system/menu/getRouters`，将 RuoYi `RouterVo` 映射为 Vben route。
5. `vite.config.ts`：开发代理目标改为 RuoYi 服务，不再指向 Nitro mock；`.env.development` 关闭 `VITE_NITRO_MOCK`。

---

## 第五部分：Python FastAPI 服务设计

### 5.1 完整目录

```text
fund-quant/
├── pyproject.toml
├── requirements.txt
├── .env.example
├── Dockerfile
├── app/
│   ├── main.py
│   ├── api/
│   │   ├── dependencies.py                 # X-Internal-Token、request-id
│   │   └── routes/
│   │       ├── health.py
│   │       ├── data.py
│   │       ├── analysis.py
│   │       └── market.py
│   ├── core/
│   │   ├── config.py                       # Pydantic Settings、阈值、算法版本
│   │   ├── errors.py
│   │   └── logging.py
│   ├── schemas/
│   │   ├── common.py
│   │   ├── fund.py
│   │   ├── trend.py
│   │   ├── signal.py
│   │   ├── market.py
│   │   └── portfolio.py
│   ├── providers/
│   │   ├── base.py                         # Provider 抽象与标准字段
│   │   └── akshare_provider.py             # 唯一允许读取 AkShare 中文列名的位置
│   ├── services/
│   │   ├── fund_data_service.py
│   │   ├── trend_service.py
│   │   ├── signal_service.py
│   │   ├── market_temperature_service.py
│   │   └── portfolio_risk_service.py
│   └── calculators/
│       ├── indicators.py
│       ├── drawdown.py
│       ├── scoring.py
│       └── portfolio.py
└── tests/
    ├── fixtures/
    ├── test_indicators.py
    ├── test_signal.py
    ├── test_market_temperature.py
    └── test_portfolio_risk.py
```

### 5.2 内部接口

| Method | Path | 用途 |
|---|---|---|
| GET | `/health/live` | 进程存活 |
| GET | `/health/ready` | 配置与 TA-Lib 就绪 |
| GET | `/internal/v1/data/funds` | 基金主数据标准化 |
| GET | `/internal/v1/data/nav/{code}` | 指定日期范围净值 |
| GET | `/internal/v1/data/estimate/{code}` | 最新估值 |
| GET | `/internal/v1/data/holdings/{code}` | 最新季报持仓、行业 |
| POST | `/internal/v1/analysis/trend` | 120 日趋势 |
| POST | `/internal/v1/analysis/signal` | 买卖信号 |
| POST | `/internal/v1/analysis/portfolio-risk` | 组合风险 |
| GET | `/internal/v1/market/temperature` | 温度原始指标与计算结果 |

### 5.3 统一响应与错误

成功：

```json
{
  "success": true,
  "data": {},
  "requestId": "01J...",
  "algorithmVersion": "trend-v1.0.0"
}
```

失败：

```json
{
  "success": false,
  "error": {
    "code": "INSUFFICIENT_NAV_DATA",
    "message": "至少需要120条有效净值",
    "retryable": false,
    "details": { "required": 120, "actual": 87 }
  },
  "requestId": "01J..."
}
```

错误码至少包含：`INVALID_FUND_CODE`、`FUND_NOT_FOUND`、`INSUFFICIENT_NAV_DATA`、`DATA_PROVIDER_UNAVAILABLE`、`DATA_PROVIDER_SCHEMA_CHANGED`、`CALCULATION_ERROR`、`UNAUTHORIZED_INTERNAL_CALL`。

### 5.4 AkShare 适配策略

- 公募基金基础信息、历史净值、净值估算、基金持仓与行业配置均通过 AkShare 公募基金数据接口获取；接口名与列名只配置在 provider 层。
- 每个适配函数先检查必需列集合，再重命名为英文 schema，再做 `to_numeric/to_datetime`，禁止业务计算直接访问中文列名。
- 网络调用用线程池隔离，因为 AkShare/Pandas 调用是同步阻塞；FastAPI 路由使用 `run_in_threadpool`。
- 单次批量同步限制代码数量；对外部源做指数退避 2 次，仅重试网络/5xx，不重试 schema 错误。
- 官方文档确认 AkShare 提供公募基金基础信息、实时行情、历史净值、净值估算、基金持仓与行业配置数据；实现时为实际锁定的 AkShare 版本建立 fixture 契约测试。[AKShare 公募基金数据](https://akshare.akfamily.xyz/data/fund/fund_public.html)

---

## 第六部分：前后端接口设计

### 6.1 公共约定

- 基础路径由部署网关决定，以下均为 Spring Boot 业务路径。
- 认证：`Authorization: Bearer <token>`。
- 时间：ISO-8601，服务端 `OffsetDateTime`，示例 `2026-07-19T14:35:00+08:00`。
- 比例字段统一返回百分数，例如 `drawdown=12.35` 表示 12.35%，不是 0.1235。
- 成功详情响应：`{ "code": 200, "msg": "操作成功", "data": ... }`。
- 失败响应：沿用 RuoYi `R.fail`，业务错误通过明确 `msg` 与可选 `data.errorCode` 返回。

### 6.2 `GET /fund/list`

请求：`pageNum`、`pageSize`、`fundCode?`、`fundName?`、`fundType?`、`status?`。

响应：

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [
    {
      "fundCode": "000001",
      "fundName": "华夏成长混合",
      "fundType": "混合型",
      "latestNav": 1.2345,
      "navDate": "2026-07-18",
      "estimateNav": 1.2411,
      "estimateGrowthRate": 0.53,
      "estimateTime": "2026-07-19T14:35:00+08:00",
      "isStale": false
    }
  ],
  "total": 1
}
```

### 6.3 `GET /fund/detail/{code}`

可选参数 `period=1m|3m|6m|1y|3y|5y|all`，默认 `3m`；按自然时间过滤，`all` 表示成立以来。

```json
{
  "code": 200,
  "data": {
    "fundCode": "000001",
    "fundName": "华夏成长混合",
    "fundType": "混合型",
    "managerName": "某基金管理人",
    "establishDate": "2001-12-18",
    "riskLevel": "R3",
    "latestNav": 1.2345,
    "navDate": "2026-07-18",
    "estimate": {
      "estimateNav": 1.2411,
      "estimateGrowthRate": 0.53,
      "estimateTime": "2026-07-19T14:35:00+08:00",
      "isStale": false
    },
    "navSeries": [
      { "date": "2026-07-18", "unitNav": 1.2345, "accumulatedNav": 3.8123 }
    ]
  }
}
```

### 6.4 `GET /fund/estimate/{code}`

```json
{
  "code": 200,
  "data": {
    "fundCode": "000001",
    "estimateNav": 1.2411,
    "estimateGrowthRate": 0.53,
    "previousNav": 1.2345,
    "previousNavDate": "2026-07-18",
    "estimateTime": "2026-07-19T14:35:00+08:00",
    "source": "AKSHARE",
    "isStale": false
  }
}
```

### 6.5 `GET /fund/trend/{code}`

```json
{
  "code": 200,
  "data": {
    "fundCode": "000001",
    "tradeDate": "2026-07-18",
    "score": 85,
    "trend": "bull",
    "ma5": 1.2381,
    "ma10": 1.2290,
    "ma20": 1.2100,
    "ma60": 1.1800,
    "ma120": 1.1300,
    "rsi": 61.25,
    "macd": { "dif": 0.0211, "dea": 0.0168, "hist": 0.0086, "cross": "GOLDEN" },
    "drawdown": 12.35,
    "volatility": 18.62,
    "algorithmVersion": "trend-v1.0.0",
    "calculatedAt": "2026-07-19T19:00:00+08:00"
  }
}
```

### 6.6 `GET /fund/signal/{code}`

```json
{
  "code": 200,
  "data": {
    "fundCode": "000001",
    "tradeDate": "2026-07-18",
    "signal": "BUY",
    "score": 88,
    "reason": [
      { "code": "MA_BULLISH", "message": "MA20高于MA60", "passed": true },
      { "code": "MACD_GOLDEN_CROSS", "message": "MACD形成金叉", "passed": true },
      { "code": "RSI_HEALTHY", "message": "RSI处于健康区间", "passed": true }
    ],
    "algorithmVersion": "signal-v1.0.0",
    "calculatedAt": "2026-07-19T19:00:01+08:00"
  }
}
```

### 6.7 `GET /market/temperature`

```json
{
  "code": 200,
  "data": {
    "tradeDate": "2026-07-19",
    "score": 25,
    "level": "低估",
    "items": [
      { "code": "CSI300_PB", "name": "沪深300 PB", "value": 1.24, "percentile": 18.2, "score": 18.2, "weight": 0.15, "isStale": false },
      { "code": "CSI500_PB", "name": "中证500 PB", "value": 1.81, "percentile": 27.4, "score": 27.4, "weight": 0.15, "isStale": false },
      { "code": "CHINEXT_PB", "name": "创业板 PB", "value": 3.12, "percentile": 31.8, "score": 31.8, "weight": 0.15, "isStale": false },
      { "code": "NORTHBOUND_FLOW", "name": "北向资金", "value": -12.6, "score": 35.0, "weight": 0.25, "isStale": false },
      { "code": "EQUITY_BOND_SPREAD", "name": "股债收益差", "value": 5.42, "percentile": 82.0, "score": 18.0, "weight": 0.30, "isStale": false }
    ],
    "calculatedAt": "2026-07-19T15:10:00+08:00"
  }
}
```

### 6.8 `POST /portfolio/risk`

持久化组合请求：

```json
{ "portfolioId": 1936227346000000001 }
```

临时组合请求：

```json
{
  "items": [
    { "fundCode": "000001", "weight": 40.00 },
    { "fundCode": "110022", "weight": 35.00 },
    { "fundCode": "161725", "weight": 25.00 }
  ]
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "riskLevel": "B+",
    "riskScore": 44,
    "overlapRate": 68.00,
    "industryRate": 55.00,
    "volatility": 21.36,
    "drawdown": 12.00,
    "topIndustries": [
      { "name": "电子", "rate": 55.00 },
      { "name": "医药生物", "rate": 18.20 }
    ],
    "algorithmVersion": "portfolio-risk-v1.0.0",
    "calculatedAt": "2026-07-19T15:20:00+08:00"
  }
}
```

### 6.9 前后端交互状态

| 场景 | HTTP | `code` | 前端行为 |
|---|---:|---:|---|
| 成功 | 200 | 200 | 更新页面 |
| 参数错误 | 400 | 500/业务码 | 表单字段提示或消息提示 |
| 未登录 | 401 | 401 | 复用现有退出登录逻辑 |
| 无权限 | 403 | 403 | 403 页面 |
| 基金不存在 | 404 | 500 + `FUND_NOT_FOUND` | 详情页提示并返回列表 |
| Python 不可用且有旧数据 | 200 | 200 | 显示数据及“已过期”标签 |
| Python 不可用且无旧数据 | 503 | 500 + `QUANT_SERVICE_UNAVAILABLE` | 保留页面框架，允许重试 |

---

## 第七部分：任务拆分清单

### A. 基础兼容

- [ ] 后端 `java.version` 改为 21，并确认本机/CI/镜像均使用 JDK 21。
- [ ] 启用 PostgreSQL JDBC，修改 dev/prod 数据源，导入 RuoYi PostgreSQL 基础脚本。
- [ ] 执行 `postgres_fund_quant_v1.sql`。
- [ ] Vben 响应成功码改为 200，分页映射 `rows/total`。
- [ ] Vben 登录、用户、权限码、菜单接口适配现有 RuoYi。
- [ ] 开发代理切到 RuoYi，关闭 Nitro mock。

### B. 后端模块骨架

- [ ] 新建 `ruoyi-fund`、父 POM module、admin dependency。
- [ ] 建立 Entity/BO/DTO/VO/Mapper/Service/Controller。
- [ ] 配置共享表多租户排除和组合表租户隔离。
- [ ] 配置量化服务 URL、超时、内部 Token、request-id 透传。
- [ ] 新增菜单、权限标识并分配给管理员角色。

### C. Python 服务

- [ ] 建立 FastAPI 工程、Pydantic schema、统一异常和健康检查。
- [ ] 建立 AkShare provider 标准字段与 fixture 契约测试。
- [ ] 实现基金基础信息、净值、估值、持仓、行业采集。
- [ ] 实现 MA/RSI/MACD/回撤/波动率与趋势评分。
- [ ] 实现信号规则、原因码和算法版本。
- [ ] 实现市场温度五指标与数据质量校验。
- [ ] 实现组合重合度、行业集中度、组合收益与风险评级。

### D. 数据同步与缓存

- [ ] 实现基金信息、净值、持仓/行业 upsert。
- [ ] 实现估值 Cache Aside、分布式锁、旧值降级、5 分钟落库。
- [ ] 实现趋势、信号、市场温度、组合风险快照 upsert。
- [ ] 注册 RuoYi Job 并记录单基金失败。
- [ ] 实现缓存失效与算法版本 key。

### E. 前端业务页

- [ ] 新增业务 API model 与请求函数。
- [ ] 新增基金路由、市场路由、组合路由和 i18n。
- [ ] 实现基金列表、详情与净值 ECharts。
- [ ] 实现趋势指标与 MA/RSI/MACD 图表。
- [ ] 实现信号结果和结构化原因。
- [ ] 实现市场温度仪表与分项表。
- [ ] 实现组合编辑、权重校验和风险结果。
- [ ] 实现 loading/empty/error/stale/insufficient-data 状态。

### F. 验收清单

- [ ] 同一基金估值并发请求只回源一次。
- [ ] 120 条已知净值的 Python 指标与基准 fixture 一致。
- [ ] 金叉、死叉、RSI 边界、最大回撤边界均有测试用例。
- [ ] 温度五项权重总和严格等于 1，分数在 0~100。
- [ ] 组合权重不等于 100% 时后端拒绝；跨租户组合访问被拒绝。
- [ ] Python 不可用时缓存降级符合接口约定。
- [ ] 页面在无数据、过期数据、接口错误下均不显示伪实时结果。

---

## 第八部分：开发优先级

### P0：工程可联调（第 1 个迭代）

1. JDK 21、PostgreSQL、Vben/RuoYi 认证和响应协议适配。
2. `ruoyi-fund` 与 `fund-quant` 骨架、内部鉴权、健康检查。
3. DDL、基金基础信息/净值同步、基金列表与详情。
4. 实时估值 Redis、防击穿、旧数据降级。

完成标准：用户能登录，访问 `/fund/list`、`/fund/detail`，看到来自 PostgreSQL/AkShare 的基金与带时间戳估值。

### P1：单基金决策闭环（第 2 个迭代）

1. Python 指标计算与基准测试。
2. 趋势接口、快照、缓存和 `/fund/trend`。
3. 信号规则、原因码、快照和 `/fund/signal`。
4. 净值、MA、MACD、RSI 图表与数据不足状态。

完成标准：同一净值序列可重复得到相同趋势/信号，结果带算法版本和可解释原因。

### P2：市场环境（第 3 个迭代）

1. 三指数 PB、北向资金、股债收益差 provider。
2. 五年百分位、数据质量校验、定时任务与快照。
3. `/market/temperature` 页面与历史趋势。

完成标准：温度分可追溯到每个原始指标、权重、来源时间和算法版本。

### P3：组合风险（第 4 个迭代）

1. 持仓/行业同步、组合 CRUD 与租户权限。
2. 组合净值对齐、重合度、行业集中度、波动率、回撤。
3. `/portfolio/risk` 编辑与结果页、缓存与风险快照。

完成标准：2~20 只基金组合可计算，跨租户不可访问，报告期和数据缺口在结果中明确展示。

### 发布门槛

- P0~P3 对应的单元测试、集成测试、类型检查和构建全部通过。
- PostgreSQL DDL 在全新库和已有 RuoYi PostgreSQL 库各执行一次。
- AkShare provider fixture 契约测试通过，外部字段变化会失败关闭而不是静默错算。
- 所有页面显示数据时间、算法版本或过期状态；不出现“实时”但无来源时间的结果。
