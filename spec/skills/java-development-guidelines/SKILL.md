---
name: java-development-guidelines
description: "Java 后端工程行为与实现规范。用于新增、修改或评审 Spring Boot、REST API、Java、MyBatis 代码，约束需求分析、修改范围、分层、BO/VO、Service、SQL、事务、幂等、并发、测试和代码评审；按任务加载专题规则。"
---

# Java 后端开发规范

以最小、可维护、符合既有架构的改动交付需求。不要为了快速实现、理论完整或未来假设破坏当前模块的边界。

## 规则优先级

1. 用户本次明确要求，以及仓库/模块的 `AGENTS.md`
2. 当前模块的既有结构、公共组件和命名惯例
3. 本 Skill 及其相关专题文件
4. 官方框架规范
5. 个人习惯

本 Skill 只约束新增或修改的代码。发现与当前需求无关的历史问题，记录而不顺手重构。

## 规则类型与冲突处理

- 安全、权限隔离、数据一致性、敏感信息保护和已发布接口兼容性是必须满足的结果；优先复用模块既有的等效实现，不以本 Skill 为由另起一套机制。
- 命名、模型后缀、分页实现、方法行数和具体技术形式属于默认约定。模块已有不同但等效的稳定模式时，保持该模式并在交付说明中说明原因。
- 不得借“沿用历史”规避安全、权限、数据或兼容性风险；无法判断现有实现是否满足上述结果时，先查代码与配置，再决定是否需要追问。

## 执行顺序

1. **任何修改 Java 代码、Mapper XML/SQL、新增接口或进行 Java 代码评审的任务，都必须先阅读 [behavior.md](behavior.md)，不得跳过。**
2. 从当前修改点开始，按 [behavior.md](behavior.md) 的范围控制读取相关 Controller、Service、Entity、Mapper/Repository、模型、直接调用方、相邻实现和已有测试；不存在或与任务无关的层不强制读取。
3. 根据工作内容在编辑前加载下列所有相关专题，不加载无关文件：

| 工作内容 | 必读专题 |
| --- | --- |
| 接口、分层、BO/VO、校验、权限、事务、异常、转换 | [architecture.md](architecture.md) |
| 新增或修改 HTTP 接口、前后端契约或接口兼容性 | [api.md](api.md) |
| Service 用例、集合、外部调用、复杂度或性能 | [service.md](service.md) |
| 导入、同步、审批、批量操作、定时任务、并发写入或关键日志 | [reliability.md](reliability.md) |
| MyBatis、Mapper XML、查询、分页、索引或数据库迁移 | [mybatis.md](mybatis.md) |
| Excel 导入、导出或大批量文件数据处理 | [excel.md](excel.md) |
| 文件上传、文件存储或文件路径处理 | [files.md](files.md) |
| 配置文件、环境参数、密钥或 Maven 依赖 | [platform.md](platform.md) |
| 使用代码生成器生成或更新 Java 业务代码 | [generation.md](generation.md) |
| 新增/修改测试，或评估测试覆盖 | [testing.md](testing.md) |
| 代码审查、PR 审查或上线前风险检查 | [review.md](review.md) |
| 使用 RuoYi-Vue-Plus，且相邻模块已采用其 BO/VO、响应或分页约定 | [references/ruoyi-vue-plus.md](references/ruoyi-vue-plus.md) |
| 修改 `fund-admin/` 或联调 `fund-quant` | [references/fund-admin.md](references/fund-admin.md) |
| 完成 Java 或 Mapper XML 改动后进行规则静态扫描 | [enforcement.md](enforcement.md) |

## 基本要求

- 先复用已有能力，再做简单扩展，最后才新增抽象或依赖。
- 修改前确定输入、输出、状态变化、权限、事务、异常和数据影响；复杂需求先给出简短设计，再编码。
- 完成 Java 或 Mapper XML 改动后，运行 [enforcement.md](enforcement.md) 中适用的静态扫描；修复错误级结果，并人工判断告警。
- 输出时说明设计取舍、修改文件、对既有模块或数据的影响，以及实际执行或未执行的验证。
