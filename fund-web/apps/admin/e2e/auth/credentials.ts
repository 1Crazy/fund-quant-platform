export const authStorageStatePath = 'e2e/auth/storageState.json';

export const testAccount = {
  password: process.env.E2E_PASSWORD || 'admin123',
  username: process.env.E2E_USERNAME || 'admin',
};
