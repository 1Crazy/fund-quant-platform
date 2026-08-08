import { test as setup } from '@playwright/test';

import { authStorageStatePath, testAccount } from './credentials';
import { LoginFlow } from '../flows/login.flow';
import { LoginPage } from '../pages/login.page';

setup('登录并保存 storageState', async ({ page }) => {
  const loginPage = new LoginPage(page);
  const loginFlow = new LoginFlow(loginPage);

  await loginFlow.login(testAccount);
  await loginPage.expectAuthenticated();
  await page.context().storageState({ path: authStorageStatePath });
});
