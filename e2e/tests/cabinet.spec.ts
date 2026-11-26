import { expect, request as playwrightRequest, test } from "@playwright/test";

/** Адрес кабинета клиента на стенде. */
const CABINET_URL = "https://clients.e2e.test";

test("хост кабинета отдаёт кабинет и закрыт от поисковиков", async ({ page }) => {
  const response = await page.goto(CABINET_URL);

  expect(response?.headers()["x-robots-tag"]).toBe("noindex, nofollow");
  await expect(page.getByRole("heading", { name: "Кабинет клиента" })).toBeVisible();
});

test("маршруты CRM с хоста кабинета недоступны", async () => {
  const context = await playwrightRequest.newContext({
    baseURL: CABINET_URL,
    ignoreHTTPSErrors: true,
  });

  expect((await context.get("/api/auth/me")).status()).toBe(404);
  expect((await context.get("/api/client-app-admin/settings")).status()).toBe(404);
  await context.dispose();
});
