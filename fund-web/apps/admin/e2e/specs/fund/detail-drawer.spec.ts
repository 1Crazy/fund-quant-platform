import { FundListFlow } from '../../flows/fund-list.flow';
import { FundListPage } from '../../pages/fund-list.page';

import { test } from '../../fixtures/test';

test('历史详情地址重定向到基金列表抽屉', async ({ page }) => {
  const flow = new FundListFlow(new FundListPage(page));

  await flow.verifyLegacyDetailUsesDrawer('002112');
});
