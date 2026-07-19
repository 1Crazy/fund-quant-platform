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
cd fund-admin/ruoyi-admin
mvn spring-boot:run
```

如果当前已经在 `fund-admin` 目录：

```bash
cd ruoyi-admin
mvn spring-boot:run
```

如果当前已经在 `fund-admin/ruoyi-admin` 目录，只有一条命令：

```bash
mvn spring-boot:run
```

日常启动不需要重复执行：

- `source "$HOME/.sdkman/bin/sdkman-init.sh"`：仅在当前终端未自动初始化 SDKMAN 时需要。
- `-Pdev`：项目的 `dev` Profile 已设为默认激活。
- `-pl ruoyi-admin -am`：首次 `mvn install` 后，直接在 `ruoyi-admin` 模块启动即可。
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

安装完成后，日常启动仍使用：

```bash
cd ruoyi-admin
mvn spring-boot:run
```

通常不需要每天重复执行 `mvn install`。

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

### 5. 初始化基金实时估值表

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin

psql -h localhost -p 5432 -U postgres -d fund_quant \
  -f script/sql/postgres/postgres_fund_realtime.sql
```

该脚本包含：

- `fund_info`
- `fund_nav`
- `fund_estimate`
- 业务索引
- RuoYi 基金菜单
- 管理员角色菜单授权

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

上游服务未配置时，后端仍可启动；但实时估值没有缓存或历史快照时，接口不会产生实时数据。

Python FastAPI 服务启动后配置：

```bash
export FUND_ESTIMATE_PROVIDER_URL='http://localhost:8000/internal/v1/data/estimate/{code}'
```

然后启动后端：

```bash
cd /Users/hong/Documents/my-project/jj/fund-admin
source "$HOME/.sdkman/bin/sdkman-init.sh"
mvn -pl ruoyi-admin -am spring-boot:run -Pdev
```

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

后端启动成功不代表基金数据已经存在。确认已经导入或同步：

- `fund_info` 有基金基础信息。
- `fund_nav` 有历史净值。
- Redis 正常连接。
- Python 估值上游已配置（仅实时估值需要）。

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
