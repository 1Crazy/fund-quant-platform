import type { QuantConfigPage } from '../pages/quant-config.page';

export class QuantConfigFlow {
  constructor(private readonly quantConfigPage: QuantConfigPage) {}

  async verifyConfigurationCenter() {
    await this.quantConfigPage.goto();
    await this.quantConfigPage.expectInventory();
    await this.quantConfigPage.expectReleaseOrValidatedVersions();
  }
}
