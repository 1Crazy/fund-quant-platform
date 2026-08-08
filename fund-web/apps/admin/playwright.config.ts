import { defineConfig, devices } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL || 'http://localhost:5777';

export default defineConfig({
  testDir: './e2e',
  outputDir: './test-results/e2e',
  fullyParallel: false,
  timeout: 60_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL,
    screenshot: 'only-on-failure',
    testIdAttribute: 'data-testid',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    viewport: { height: 900, width: 1440 },
  },
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'chromium',
      dependencies: ['setup'],
      testIgnore: /auth\.setup\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: 'e2e/auth/storageState.json',
      },
    },
  ],
});
