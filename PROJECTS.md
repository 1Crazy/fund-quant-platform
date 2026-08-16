# 项目环境基线

## jj / 基金量化决策系统

- Java: 21
- Maven: 3.9.x
- Node.js: 22.x
- pnpm: 10.33.3
- 后端构建入口: `fund-admin`，基金模块验证命令为
  `mvn -pl ruoyi-modules/ruoyi-fund -am clean test -DskipTests=false`
- 前端构建入口: `fund-web`，管理端验证命令为
  `pnpm --filter admin typecheck`、`pnpm --filter admin build`

说明：`package.json` 当前可能声明不同的 pnpm 版本；本机已确认的可用运行时为 pnpm 10.33.3。版本或构建脚本调整后，应更新本节。
