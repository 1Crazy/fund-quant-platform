import { expect, type Page } from '@playwright/test';

import { BasePage } from './base.page';

/** 基金列表承载详情抽屉，禁止回退为独立详情页。 */
export class FundListPage extends BasePage {
  constructor(page: Page) {
    super(page);
  }

  async gotoLegacyDetail(code: string) {
    await this.gotoPath(`/fund/detail?code=${encodeURIComponent(code)}`);
  }

  async expectDetailDrawer(code: string) {
    await expect(this.page).toHaveURL(new RegExp(`/fund/list(?:\\?[^#]*code=${code})?`));
    await expect(this.page.locator('.el-drawer')).toBeVisible();
    await expect(this.page.getByText('基金详情', { exact: true })).toBeVisible();
  }

  async closeDetailDrawer() {
    await this.page.locator('.el-drawer__close-btn').click();
    await expect(this.page.locator('.el-drawer')).toBeHidden();
    await expect(this.page).toHaveURL(/\/fund\/list(?:\?.*)?$/);
    await expect(this.page).not.toHaveURL(/(?:\?|&)code=/);
  }
}
