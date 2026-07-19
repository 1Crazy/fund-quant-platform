# 基金量化决策系统启动手册

本目录用于保存项目本地开发环境的启动、初始化和排障说明。

## 文档目录

- [后端启动手册](./backend-startup.md)
- [Python 量化服务启动手册](./python-startup.md)

## 推荐阅读顺序

1. Java 第一次启动：阅读后端启动手册的“首次初始化”部分。
2. Java 日常开发：只执行后端手册的“日常最简启动”部分。
3. Python 第一次启动：执行 Python 手册的“首次启动”部分。
4. Python 日常开发：只执行 Python 手册的“日常开发启动”部分。
5. 连接失败：查看对应手册的排障说明。

## 当前技术约定

- Java：JDK 21，由 SDKMAN 管理。
- Python：Python 3.12，由 pyenv 管理。
- 后端：RuoYi-Vue-Plus，入口模块 `ruoyi-admin`。
- 数据库：PostgreSQL 17。
- 缓存：Redis 8 或本机已有 Redis。
- 后端端口：`8080`。
- Python 量化服务端口：`8000`。
- 前端目录：`fund-web/apps/admin`。
