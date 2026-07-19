# 基金量化决策系统启动手册

本目录用于保存项目本地开发环境的启动、初始化和排障说明。

## 文档目录

- [后端启动手册](./backend-startup.md)

## 推荐阅读顺序

1. 第一次启动：阅读后端启动手册的“首次初始化”部分。
2. 日常开发：只执行“日常最简启动”部分。
3. 连接失败：查看“常见问题”。

## 当前技术约定

- Java：JDK 21，由 SDKMAN 管理。
- Python：Python 3.12，由 pyenv 管理。
- 后端：RuoYi-Vue-Plus，入口模块 `ruoyi-admin`。
- 数据库：PostgreSQL 17。
- 缓存：Redis 8 或本机已有 Redis。
- 后端端口：`8080`。
- 前端目录：`fund-web/apps/admin`。
