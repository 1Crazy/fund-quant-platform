import type { FundListPage } from '../pages/fund-list.page';

export class FundListFlow {
  constructor(private readonly fundListPage: FundListPage) {}

  async verifyLegacyDetailUsesDrawer(code: string) {
    await this.fundListPage.gotoLegacyDetail(code);
    await this.fundListPage.expectDetailDrawer(code);
    await this.fundListPage.closeDetailDrawer();
  }
}
