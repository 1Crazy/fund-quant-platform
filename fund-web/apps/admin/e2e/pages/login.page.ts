import { expect, type Locator, type Page } from '@playwright/test';

import { BasePage } from './base.page';

export interface LoginAccount {
  password: string;
  username: string;
}

export class LoginPage extends BasePage {
  constructor(page: Page) {
    super(page);
  }

  private get passwordInput(): Locator {
    return this.page.getByRole('textbox', { name: '密码' });
  }

  private get submitButton(): Locator {
    return this.page.getByRole('button', { name: /^(?:登录|login)$/i });
  }

  private get usernameInput(): Locator {
    return this.page.getByRole('textbox', { name: '请输入用户名' });
  }

  async expectAuthenticated() {
    await expect(this.page).toHaveURL(/\/fund\/list/);
    await expect(this.page.getByRole('heading', { name: '基金实时估值' })).toBeVisible();
  }

  async fill(account: LoginAccount) {
    await this.usernameInput.fill(account.username);
    await this.passwordInput.fill(account.password);
  }

  async goto() {
    await this.gotoPath('/auth/login');
    await expect(this.submitButton).toBeVisible();
  }

  async submit() {
    await this.submitButton.click();
  }
}
