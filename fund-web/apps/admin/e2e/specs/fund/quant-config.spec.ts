import { test } from '../../fixtures/test';

test('量化配置中心展示配置库存及发布状态', async ({ quantConfigFlow }) => {
  await quantConfigFlow.verifyConfigurationCenter();
});
