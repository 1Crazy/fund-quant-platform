import { expect, type Locator, type Page } from '@playwright/test';

import { BasePage } from './base.page';

export class QuantConfigPage extends BasePage {
  constructor(page: Page) {
    super(page);
  }

  private get groupButtons(): Locator {
    return this.page.locator('.group-list .group-item');
  }

  private get publishHistoryTab(): Locator {
    return this.page.getByRole('tab', { name: '发布历史' });
  }

  private get releaseRows(): Locator {
    return this.page
      .getByRole('tabpanel', { name: '发布历史' })
      .locator('tbody tr');
  }

  private get versionRows(): Locator {
    return this.page
      .getByRole('tabpanel', { name: '版本列表与 Diff' })
      .locator('tbody tr');
  }

  private get versionsTab(): Locator {
    return this.page.getByRole('tab', { name: '版本列表与 Diff' });
  }

  async expectInventory() {
    await expect(this.groupButtons).toHaveCount(10);

    await this.versionsTab.click();
    await expect(this.versionRows).not.toHaveCount(0);
    await expect(
      this.versionRows.filter({ hasText: /已校验|已发布/ }),
    ).not.toHaveCount(0);
  }

  async expectReleaseOrValidatedVersions() {
    await this.publishHistoryTab.click();
    const releaseCount = await this.releaseRows.count();
    if (releaseCount > 0) {
      await expect(this.releaseRows.first()).toContainText('已发布');
      return;
    }

    await this.versionsTab.click();
    await expect(this.versionRows.filter({ hasText: '已校验' })).not.toHaveCount(0);
  }

  async goto() {
    await this.gotoPath('/fund/config');
    await expect(
      this.page.getByRole('heading', { name: '量化配置中心' }),
    ).toBeVisible();
  }
}
