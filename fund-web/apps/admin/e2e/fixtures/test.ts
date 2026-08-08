import { test as base } from '@playwright/test';

import { QuantConfigFlow } from '../flows/quant-config.flow';
import { QuantConfigPage } from '../pages/quant-config.page';

interface FundFixtures {
  quantConfigFlow: QuantConfigFlow;
}

export const test = base.extend<FundFixtures>({
  quantConfigFlow: async ({ page }, use) => {
    await use(new QuantConfigFlow(new QuantConfigPage(page)));
  },
});

export { expect } from '@playwright/test';
