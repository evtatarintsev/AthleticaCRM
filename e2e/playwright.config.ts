import { defineConfig, devices } from "@playwright/test";

/** Прогон смоук-набора на стенде e2e (`e2e/stand`): CRM доступна раннеру по `app.e2e.test`. */
export default defineConfig({
  testDir: "tests",
  globalSetup: "./support/globalSetup.ts",
  fullyParallel: true,
  forbidOnly: true,
  retries: 0,
  reporter: [["list"], ["html", { open: "never", outputFolder: "playwright-report" }]],
  use: {
    baseURL: "https://app.e2e.test",
    ignoreHTTPSErrors: true,
    locale: "ru-RU",
    extraHTTPHeaders: { "Accept-Language": "ru" },
    timezoneId: "Europe/Moscow",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        // Сертификат стенда самоподписанный: без флага Chromium засыпает лог ошибками рукопожатия.
        launchOptions: { args: ["--ignore-certificate-errors"] },
      },
    },
  ],
});
