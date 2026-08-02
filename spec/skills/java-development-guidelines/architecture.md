# 分层、模型与边界

遵循当前模块的架构，而不是为简单需求引入新的 DDD 分层。本仓库常见结构为 `Controller -> Service -> Mapper`；仅在相邻模块已有应用服务、领域服务或防腐层时才沿用。

| 层 | 负责 | 禁止 |
| --- | --- | --- |
| Controller | 协议绑定、`@Validated`、鉴权注解、调用 Service、统一响应 | 直接调 Mapper、业务判断、事务、数据拼装、循环查询 |
| Service | 用例编排、业务校验、事务边界、调用 Mapper 和外部服务 | 接收 HTTP 对象、拼展示文案、承担复杂转换细节 |
| Mapper/Repository | 持久化、筛选、关联、排序、分页和数据库聚合 | 业务状态机、权限决策、跨系统编排、展示语义 |
| Entity | 数据库持久化字段 | API 入参/出参、页面字段、业务计算结果 |
| Converter/Assembler/Calculator | 显式模型转换、字段补全、独立计算规则 | HTTP 访问、隐式事务、整个业务流程 |

## 接口模型与命名

- `Entity` 只表示数据库模型，严禁直接作为接口入参或响应。
- 沿用模块已有请求和响应模型命名；输入模型必须表达查询、创建、更新或批量操作的意图，输出模型必须表达列表、详情或导出用途。不要混入无约定、用途不明的 `*DTO`。
- 字段、校验分组或写入权限差异明显时，拆分对应的输入模型，防止越权写入；字段差异明显时拆分列表、详情和导出响应模型。字段完全一致时复用模块既有模型即可。
- 使用 RuoYi-Vue-Plus 且相邻代码已采用 BO/VO、`PageQuery` 或统一响应类型时，按需读取 [references/ruoyi-vue-plus.md](references/ruoyi-vue-plus.md)，不要把其目录和后缀约定施加到其他项目。
- 第三方协议模型放在明确的 `client`、`api` 或 `model` 边界，不透传供应方响应，不污染内部 BO/VO。
- 禁止 `UserDTO`、`CommonDTO`、`ResultData`、`processData`、`handleInfo` 等无业务语义命名。金额、时间、数量和状态要在字段名或必要注释中表达单位与含义。
- 新增类放入已有业务模块和包结构，包名表达领域，例如 `fund`、`sync`、`report`、`trade`；禁止新增 `common`、`helper`、`manager`、`utils2`、`temp`、`testHelper` 等职责不清的万能包。

## 校验、权限与转换

- Controller 使用 `@Validated`，请求体使用 `@Validated(...)` 或 `@Valid`；嵌套对象和集合元素递归校验。
- `@NotBlank`、`@NotNull`、`@Size`、`@Pattern`、`@Min`、`@Max` 等处理结构合法性。不要在 Controller 重复手写空值、长度或格式 `if/else`。
- `@PathVariable`、`@RequestParam` 和 `@RequestBody` 都校验非法输入，ID 使用项目约定类型。业务请求不直接使用 `Map<String, Object>`，URL 参数不传递令牌、密钥等敏感信息；批量请求的集合大小必须受限。
- 存在性、状态、库存/余额、唯一性、关联关系和操作权限属于 Service 业务校验。Controller 的按钮权限不是数据权限；更新、删除、导出和详情必须校验租户、部门、用户数据范围和资源归属，不能根据前端传入的 `userId`、`tenantId` 或角色判断权限。
- 数据库唯一性和引用完整性必须用约束兜底，Java 预检只用于改善错误提示。
- 字段完全一致、没有业务转换且项目已有统一工具时，才允许 Bean 拷贝。涉及字典/状态转换、金额/时间格式化、多字段组合、子列表或权限字段时，使用命名明确的 Converter/Assembler，禁止大范围 `BeanUtil.copyProperties`、`BeanUtils.copyProperties` 或散落在 Controller、长 Service 方法中。

## 输入边界与数据类型

- 所有用户输入限制长度、数量和大小：字符串使用校验注解，分页使用最大页大小，文件使用大小/行数限制，批量 BO 的集合必须有最大数量。禁止直接接收超大集合或无界分页。
- 状态、类型、分类和操作类型使用项目已有枚举或常量，禁止散落数字判断如 `if (status == 1)`。新建持久化枚举应提供稳定的 `code` 和用户可理解的 `description`，或使用项目等价字段；数据库值必须与枚举语义保持一致。
- Controller 不接收无语义的数字状态，数据库不存中文状态。将如“是否完成”的语义封装为 `OrderStatus.isCompleted()` 等枚举方法或项目等价能力，避免 Service 散落字符串/数字比较。
- 时间统一使用项目约定的 Java 类型和数据库类型，不用 `String` 存储时间。涉及时区、跨日、定时任务或对外协议时，明确时区、格式和边界。
- 金额禁止使用 `double`、`float`；使用 `BigDecimal` 并明确 `scale` 和 `RoundingMode`，数据库 `DECIMAL` 精度与业务精度保持一致。

## 事务、异常与安全边界

- 事务仅定义在 Service 的公开用例方法；只包裹必须原子提交的写操作。纯查询不加事务，不在数据库事务内做外部慢调用、文件处理或长计算。
- 注意 Spring 代理边界：同类内部调用不会触发新的事务配置。不得吞掉异常后继续返回成功；需要恢复时明确补偿和失败语义。
- 使用项目统一业务异常和全局异常处理；禁止把 `RuntimeException`、`Exception` 或无上下文的运行时异常作为业务失败直接抛出，也禁止 Controller 手工返回 `{success:false,msg:...}`。异常消息说明业务原因，不能暴露 SQL、表名、堆栈或内部路径。
- 新接口沿用相邻接口的认证、权限和数据权限注解；响应只返回最小必要字段，日志不得记录密码、令牌、证件号、完整请求或完整第三方响应。
