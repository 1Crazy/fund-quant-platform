# E2E Testing

本目录存放 `apps/admin` 的 Playwright Test 测试。目录分层与项目 TPM 前端一致：

- `pages/`：页面定位器与页面级操作。
- `flows/`：跨页面或业务操作编排。
- `fixtures/`：统一注入 Flow。
- `auth/`：登录 setup 与临时 `storageState`。
- `specs/`：只表达场景和业务断言，不直接使用 locator。

默认地址为 `http://localhost:5777`，可通过 `E2E_BASE_URL` 覆盖。登录账户从 `E2E_USERNAME` 和 `E2E_PASSWORD` 读取；开发环境默认使用 `admin/admin123`。

```bash
E2E_BASE_URL=http://localhost:5777 pnpm --filter admin test:e2e
```

量化配置用例不创建草稿、不触发校验、不发布版本。它仅验证已登录用户能够进入配置中心，并在存在首发发布记录或待发布的已校验版本时正确展示对应状态。
