# 后端启动手册

适用于：

```text
/Users/hong/Documents/my-project/jj/fund-admin
```

后端启动入口：

```text
ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java
```

## 一、日常最简启动（推荐）

前提：首次 `mvn install` 已完成，PostgreSQL 和 Redis 正在运行，当前终端的 `java -version` 是 JDK 21。

如果当前在项目根目录 `/Users/hong/Documents/my-project/jj`：

```bash
cd fund-admin
make dev
```

如果当前已经在 `fund-admin` 目录：

```bash
make dev
```

如果当前已经在 `fund-admin/ruoyi-admin` 目录，同样只有一条命令：

```bash
make dev
```

两个目录的 `make dev` 都会回到 Maven 聚合工程，先增量安装最新 `ruoyi-fund`，再以本地热部署模式启动 `ruoyi-admin`。该模式会把 `ruoyi-fund/target/classes` 直接加入运行类路径，避免复用 `~/.m2` 中可能过期的业务模块 Jar。
保存 `ruoyi-admin` 或 `ruoyi-fund` 的 Java、Mapper XML、YAML 或 properties 后，`make dev` 内置的本地轮询器会自动增量编译，Spring Boot DevTools 随后重载应用上下文；浏览器刷新即可验证，不需要手动停止和启动 Java 服务。

不要直接运行 `mvn spring-boot:run`：它不会启用项目的 `hot-run` 类路径配置，可能直接复用 `~/.m2` 中旧的业务模块 Jar。若要显式使用热部署目标，执行：

```bash
cd fund-admin
make dev-hot
```

日常启动不需要重复执行：

- `source "$HOME/.sdkman/bin/sdkman-init.sh"`：仅在当前终端未自动初始化 SDKMAN 时需要。
- `-Pdev`：项目的 `dev` Profile 已设为默认激活。
- `-pl ruoyi-admin -am`：已封装在 `make dev` 的增量准备阶段，不需要手动输入。
- `clean package`：这是打包流程，不是日常开发启动流程。

只有当 `java -version` 不是 21 时，才执行：

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java <JDK-21标识>
```

启动说明：

- `spring-boot:run`：直接启动 Spring Boot，不需要手动打包和执行 JAR。

启动成功后访问：

```text
http://localhost:8080
```

## 二、首次安装 Maven 依赖

第一次启动项目，或修改了 Maven 依赖、父 POM、模块 POM 后，先在后端根目录安装依赖：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

source "$HOME/.sdkman/bin/sdkman-init.sh"

mvn install -Pdev -DskipTests
```

该命令会安装 RuoYi 父工程、公共模块、业务模块及 `ruoyi-admin` 所需依赖。首次执行需要联网下载依赖，耗时取决于 Maven 仓库连接速度。

安装完成后，日常启动使用：

```bash
make dev
```

`make dev` 只会增量编译发生变化的模块；没有源码变化时不会进行完整重建。修改 Maven 依赖、POM、启动 JVM 参数或数据库迁移后，仍需结束当前进程并重新执行 `make dev`，这些变更不属于应用上下文热重载范围。

如果本地没有启动 SnailJob 或 Spring Boot Admin，使用下面这个本地开发命令：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

source "$HOME/.sdkman/bin/sdkman-init.sh"

mvn -pl ruoyi-admin -am spring-boot:run -Pdev \
  -Dspring-boot.run.arguments="--snail-job.enabled=false --spring.boot.admin.client.enabled=false"
```

## 三、首次初始化

### 1. 确认 JDK 21

在启动后端的同一个终端执行：

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk current java
java -version
mvn -version
```

`java -version` 与 `mvn -version` 应显示 Java 21。

如果 SDKMAN 已安装多个 JDK 版本，先选择已安装的 JDK 21：

```bash
sdk list java
sdk use java <JDK-21标识>
```

`sdk use` 只对当前终端生效；如果希望固定当前用户默认版本：

```bash
sdk default java <JDK-21标识>
```

### 2. 确认 Python 3.12

基金实时估值后续会调用 Python 量化服务。当前项目使用 pyenv：

```bash
eval "$(pyenv init -)"
pyenv version
python --version
```

应显示 Python 3.12.x。

### 3. 启动 PostgreSQL 17

如果本机没有 PostgreSQL，可以使用项目 Docker 配置：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

docker compose \
  -f script/docker/database.yml \
  up -d postgres
```

当前开发配置：

```text
地址：localhost:5432
数据库：fund_quant
用户名：postgres
密码：postgres
```

### 4. 初始化 RuoYi 基础表

先导入 RuoYi 基础表，再导入业务表：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

psql -h localhost -p 5432 -U postgres -d fund_quant \
  -f script/sql/postgres/postgres_ry_vue_5.X.sql
```

### 5. 初始化基金数据中心与实时估值表

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

psql -h localhost -p 5432 -U postgres -d fund_quant \
  -f script/sql/postgres/postgres_fund_realtime.sql
```

该脚本包含：

- `fund_info`
- `fund_nav`
- `fund_holding`
- `fund_sync_run`
- `fund_data_quality_issue`
- `fund_estimate`
- 业务索引
- RuoYi 基金菜单
- 管理员角色菜单授权

已有数据库升级到基金数据中心结构时执行有序 update SQL：

```bash
psql -h localhost -p 5432 -U postgres -d fund_quant \
  -f script/sql/update/postgres/update_fund_data_center_v1.sql
```

检查表是否存在：

```bash
psql -h localhost -p 5432 -U postgres -d fund_quant \
  -c "\\dt fund_*"
```

### 6. 确认 Redis

不需要强制使用 Docker。你已有本地 Redis 时，直接使用本地 Redis 即可。

当前后端开发配置位于：

```text
fund-admin/ruoyi-admin/src/main/resources/application-dev.yml
```

默认连接参数：

```text
地址：localhost
端口：6379
数据库：0
密码：无
```

验证本地 Redis：

```bash
redis-cli -h localhost -p 6379 ping
```

返回 `PONG` 即可。

当前开发配置已经按本地无密码 Redis 设置（无密码时必须省略 `password` 属性，不能写成空值）：

```yaml
spring.data:
  redis:
    # 不配置 password
```

如果本地 Redis 设置了密码，使用环境变量注入：

```bash
SPRING_DATA_REDIS_PASSWORD='你的密码' mvn -pl ruoyi-admin -am spring-boot:run -Pdev
```

如果端口或数据库编号不同，同步修改 `host`、`port`、`database`。

只有本机没有 Redis 时，才使用 Docker：

```bash
docker run -d \
  --name fund-redis \
  -p 6379:6379 \
  redis:8
```

## 四、后端配置说明

### 前端开发登录账号

PostgreSQL 初始化脚本中的超级管理员账号为：

```text
用户名：admin
密码：admin123
租户：000000
```

前端开发页面已默认填充该账号，并自动提交 RuoYi 客户端参数。`admin123` 是初始化脚本中 `sys_user` 管理员密码密文对应的默认密码；`sys.user.initPassword=123456` 仅用于后续新建用户的初始密码，不代表管理员当前密码。

若数据库不是使用 `script/sql/postgres/postgres_ry_vue_5.X.sql` 初始化，账号密码以实际 `sys_user` 表为准。

### PostgreSQL 配置

开发配置当前使用：

```yaml
spring:
  datasource:
    dynamic:
      datasource:
        master:
          driverClassName: org.postgresql.Driver
          url: jdbc:postgresql://localhost:5432/fund_quant
          username: postgres
          password: postgres
```

生产环境支持环境变量覆盖：

```bash
export DB_URL='jdbc:postgresql://localhost:5432/fund_quant'
export DB_USERNAME='postgres'
export DB_PASSWORD='your-password'
```

### 基金估值上游

上游服务未配置时，后端仍可启动；但本地不存在的基金无法自动同步，实时估值在没有缓存或历史快照时也不会产生数据。

Python FastAPI 服务默认地址为 `http://localhost:8000`，开发配置会自动调用。地址变化时覆盖：

```bash
export FUND_DATA_PROVIDER_BASE_URL='http://localhost:8000'
export FUND_ESTIMATE_PROVIDER_URL='http://localhost:8000/internal/v1/data/estimate/{code}'
```

基金列表采用本地库分页查询。代码、名称、类型、来源、质量状态、同步状态和历史位置等全部筛选项都只查询已同步到 PostgreSQL 的数据，不在列表读请求中触发 fund-quant 同步。

进入详情时才按需同步基金档案、净值和最新公开股票持仓。详情页按近1月、近3月、近6月、近1年、近3年、近5年、成立以来切换，Java 会按自然时间过滤净值序列。

然后启动后端：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin
make dev
```

`make dev` 会自动使用 SDKMAN 当前的 JDK 与 Maven，从父工程增量安装所有依赖模块后再启动应用。不要在 `ruoyi-admin` 子目录直接执行 `mvn spring-boot:run`：该方式会从 `~/.m2` 读取 `ruoyi-fund`，模块源码刚修改但未重新安装时会运行旧代码。

### 估值定时任务

默认配置为交易日 09:00～15:00 每 5 分钟刷新：

```yaml
fund:
  estimate:
    schedule-enabled: true
    schedule-cron: "0 */5 9-15 * * MON-FRI"
    zone-id: Asia/Shanghai
```

如果暂时没有 Python 上游服务，可以关闭定时任务：

```bash
mvn -pl ruoyi-admin -am spring-boot:run -Pdev \
  -Dspring-boot.run.arguments="--fund.estimate.schedule-enabled=false --snail-job.enabled=false --spring.boot.admin.client.enabled=false"
```

### 数据中心同步参数

数据中心同步默认开启，长批同步由 SnailJob 承载。开发环境可以通过环境变量调整供应方地址、分页、重试、限速和缓存 TTL：

```bash
export FUND_DATA_PROVIDER_BASE_URL='http://localhost:8000'
export FUND_SYNC_ENABLED=true
export FUND_SYNC_PAGE_SIZE=200
export FUND_SYNC_RETRY_MAX_ATTEMPTS=3
export FUND_SYNC_RATE_LIMIT_PER_MINUTE=60
export FUND_CACHE_INFO_TTL=30m
export FUND_CACHE_NAV_TTL=1h
export FUND_CACHE_HOLDING_TTL=6h
export FUND_CACHE_SYNC_STATUS_TTL=30s
```

需要临时禁用同步时设置 `FUND_SYNC_ENABLED=false`；查询仍读取 PostgreSQL 中最后成功版本。更完整的全量初始化、增量运行、重试和回滚说明见 [基金数据中心运行手册](../fund-data-center-runbook.md)。

## 五、打包启动方式

适用于需要模拟生产 JAR 启动的场景：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

source "$HOME/.sdkman/bin/sdkman-init.sh"

mvn -Pdev clean package -DskipTests

java -jar ruoyi-admin/target/ruoyi-admin.jar \
  --snail-job.enabled=false \
  --spring.boot.admin.client.enabled=false \
  --fund.estimate.schedule-enabled=false
```

日常开发不推荐每次执行 `clean package`；优先使用前面的 `spring-boot:run`。

## 六、IntelliJ IDEA 启动方式

1. 打开 `/Users/hong/Documents/my-project/jj/fund-admin`。
2. Project SDK 选择 JDK 21。
3. 运行 `ruoyi-admin/src/main/java/org/dromara/DromaraApplication.java`。
4. Active profile 设置为 `dev`。
5. 如果本地没有 SnailJob 和 Spring Boot Admin，在 VM options 或 Program arguments 中加入：

```text
--snail-job.enabled=false --spring.boot.admin.client.enabled=false
```

## 七、常见问题

### 1. `password authentication failed for user postgres`

检查 `application-dev.yml` 与 PostgreSQL 实际用户名、密码是否一致。也可以通过环境变量覆盖：

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
```

### 2. `Connection refused: localhost:6379`

Redis 没有启动，或端口配置不一致。检查：

```bash
redis-cli -h localhost -p 6379 ping
```

### 3. `Cannot connect to SnailJob`

本地未启动 SnailJob 时，使用：

```bash
--snail-job.enabled=false
```

### 4. 端口 8080 已被占用

查看占用进程：

```bash
lsof -n -P -iTCP:8080 -sTCP:LISTEN
```

临时改端口：

```bash
mvn -pl ruoyi-admin -am spring-boot:run -Pdev \
  -Dspring-boot.run.arguments="--server.port=8081"
```

### 5. `/fund/list` 返回空列表

不带基金代码时，列表只展示已经同步到本地业务库的数据；空库返回空列表是正常行为。

精确传入六位基金代码时也只查询本地 PostgreSQL。若仍为空，依次确认：

- PostgreSQL 中已创建 `fund_info` 表并已同步该基金。
- 对应记录的 `status = '0'` 且 `del_flag = 0`。
- 请求中的基金代码与 `fund_info.fund_code` 完全一致。

```bash
curl -i \
  -H 'Authorization: Bearer <access-token>' \
  'http://localhost:8080/fund/list?fundCode=010990&pageNum=1&pageSize=20'
```

## 八、启动成功检查

后端启动后，先检查首页：

```bash
curl -i http://localhost:8080/
```

再检查基金列表接口。该接口需要登录 Token：

```bash
curl -i \
  -H 'Authorization: Bearer <access-token>' \
  'http://localhost:8080/fund/list?pageNum=1&pageSize=20'
```

预期响应结构：

```json
{
  "code": 200,
  "msg": "查询成功",
  "rows": [],
  "total": 0
}
```
