# RuoYi-Vue-Plus 约定

仅在仓库确认使用 RuoYi-Vue-Plus，且相邻模块已采用下列类型和目录时加载。本文件是项目变体参考，不是通用 Java 规范；不同 RuoYi 分支或模块的既有约定优先。

## 模型与分层

- 请求模型通常位于 `domain.bo`，响应模型通常位于 `domain.vo`。查询、创建、更新和批量操作分别使用 `XxxQueryBo`、`XxxCreateBo`、`XxxUpdateBo`、`XxxBatchDeleteBo`；列表、详情、导出字段不同则使用 `XxxListVo`、`XxxDetailVo`、`XxxExportVo`。
- 若模块既有单个 `XxxBo` 配合 `AddGroup`、`EditGroup`、`QueryGroup`，仅在字段和权限差异小的时候沿用；差异明显时拆分 BO，避免越权写入。
- 常见链路为 `Controller -> Service -> Mapper`。Controller 不直接调用 Mapper，Entity 不作为接口入参或响应。

## 响应与分页

- 沿用相邻接口的统一响应；本项目常用 `R<T>`，管理后台分页常用 `TableDataInfo<T>`。
- 列表查询沿用项目 `PageQuery` 和统一分页字段；不要把 `PageQuery`、`TableDataInfo` 或 MyBatis `IPage` 引入未使用这些类型的模块。
- 继续遵循上级 [`api.md`](../api.md) 的最大页大小、排序白名单、导出分批和接口兼容规则。
