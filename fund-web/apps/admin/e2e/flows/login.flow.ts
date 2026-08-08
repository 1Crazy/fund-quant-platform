import type { LoginAccount, LoginPage } from '../pages/login.page';

export class LoginFlow {
  constructor(private readonly loginPage: LoginPage) {}

  async login(account: LoginAccount) {
    await this.loginPage.goto();
    await this.loginPage.fill(account);
    await this.loginPage.submit();
  }
}
