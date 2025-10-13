import { defineConfig, devices } from '@playwright/test';
import path from 'path';
import url from 'url';

// Resolve file:// URL to the local index.html
const indexPath = path.resolve(__dirname, 'index.html');
const fileUrl = url.pathToFileURL(indexPath).toString();

export default defineConfig({
  testDir: 'tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: fileUrl,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'off',
    viewport: { width: 1280, height: 900 },
  },
  projects: [
    {
      name: 'Desktop-Chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
    {
      name: 'Mobile-Chromium',
      use: {
        ...devices['Pixel 7'],
      },
    },
  ],
});


