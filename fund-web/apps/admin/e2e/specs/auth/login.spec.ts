import { testAccount } from '../../auth/credentials';
import { LoginFlow } from '../../flows/login.flow';
import { LoginPage } from '../../pages/login.page';

import { test } from '../../fixtures/test';

test.use({ storageState: { cookies: [], origins: [] } });

test('默认管理员能够登录基金量化决策系统', async ({ page }) => {
  const loginPage = new LoginPage(page);
  const loginFlow = new LoginFlow(loginPage);

  await loginFlow.login(testAccount);
  await loginPage.expectAuthenticated();
});
