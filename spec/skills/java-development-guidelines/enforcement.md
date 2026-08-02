# Java 规则静态扫描

在 Java 或 Mapper XML 改动完成后运行本扫描。它只检查高置信、可机械识别的规则，不能替代业务、权限、事务和性能评审。

```bash
bash spec/skills/java-development-guidelines/scripts/check-java-guidelines.sh --changed <repository-root>
```

不带 `--changed` 时扫描目标目录中的全部 Java 和 Mapper XML 文件：

```bash
bash spec/skills/java-development-guidelines/scripts/check-java-guidelines.sh <repository-root>
```

错误级结果会使脚本以非零状态退出，必须修复或确认扫描规则本身不适用于该项目后再调整脚本。告警级结果不阻断，但必须人工确认：

- 错误：Controller 导入 Mapper/Repository/DAO、Controller 使用 `@Transactional`、Controller 直接接收 `@RequestBody Map`、Mapper XML 使用 `SELECT *`。
- 告警：Mapper XML 使用 `${...}`、日志语句可能记录密码、令牌或密钥。

扫描会忽略 `target/`、构建产物和缓存目录。不要用注释、字符串拆分或无语义封装绕过检查；无法静态判断的 Entity 泄露、数据权限、幂等、SQL 计划和事务边界仍按对应专题人工审查。
