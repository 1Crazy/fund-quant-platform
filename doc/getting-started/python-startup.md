# Python 量化服务启动手册

本文档用于启动 `fund-quant`。项目使用 pyenv 管理 Python 3.12，服务端口为 `8000`，默认连接本机 `localhost:6379` 的 Redis DB 1。

## 一、首次启动

首次启动只需要执行一次安装命令：

```bash
cd /Users/hong/Documents/my-project/jj/fund-quant
python --version
make install
make dev
```

`make install` 会自动创建 `.venv`、安装开发依赖，并在 `.env` 不存在时从 `.env.example` 创建配置文件。

`python --version` 应显示 Python 3.12.x。项目中的 `.python-version` 已固定为 `3.12.4`，只要终端已经正常初始化 pyenv，进入目录后会自动选择该版本。

以下情况需要重新安装依赖：

- `requirements.txt` 或 `requirements-dev.txt` 发生变化。
- 删除并重新创建了 `.venv`。
- Python 3.12 的补丁版本发生变化并决定重建虚拟环境。

## 二、日常开发启动

以后每天开发只需要：

```bash
cd /Users/hong/Documents/my-project/jj/fund-quant
make dev
```

如果终端当前已经在 `fund-quant` 目录，只需要：

```bash
make dev
```

这是推荐的行业常见工程入口。`Makefile` 会直接调用 `.venv/bin/uvicorn`，因此不需要手动激活虚拟环境。底层等价命令是：

```bash
.venv/bin/uvicorn app.main:app --reload --port 8000
```

日常启动不需要重复执行：

- `python -m venv .venv`
- `pip install -r requirements-dev.txt`
- `cp .env.example .env`

## 三、启动成功检查

启动日志出现以下内容表示端口已经监听：

```text
Uvicorn running on http://127.0.0.1:8000
```

另开一个终端检查：

```bash
curl http://localhost:8000/health
```

接口文档：

```text
http://localhost:8000/docs
```

测试真实基金数据：

```bash
curl http://localhost:8000/internal/v1/data/fund/000001
curl "http://localhost:8000/internal/v1/data/nav/000001?days=10"
curl http://localhost:8000/internal/v1/data/holdings/000001
curl http://localhost:8000/internal/v1/data/estimate/000001
```

第一次请求 AkShare 时可能较慢，后续请求会使用 Redis 缓存。

## 四、与 Java 联调

Java 的 `dev` 配置已经默认通过 `http://localhost:8000` 调用 fund-quant，其中包括：

```text
http://localhost:8000/internal/v1/data/fund/{code}
http://localhost:8000/internal/v1/data/nav/{code}
http://localhost:8000/internal/v1/data/holdings/{code}
http://localhost:8000/internal/v1/data/estimate/{code}
```

因此本地使用默认端口时不需要设置环境变量。Python 启动后，再启动或重启 Java 后端即可。

只有 Python 地址变化时才需要覆盖：

```bash
export FUND_DATA_PROVIDER_BASE_URL='http://localhost:8000'
export FUND_ESTIMATE_PROVIDER_URL='http://localhost:8000/internal/v1/data/estimate/{code}'
```

## 五、停止服务

在运行 Uvicorn 的终端按：

```text
Ctrl+C
```

即可停止本次开发服务。
