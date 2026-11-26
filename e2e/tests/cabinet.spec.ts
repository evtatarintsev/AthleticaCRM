import { randomInt } from "node:crypto";
import { request as playwrightRequest } from "@playwright/test";
import { uuidv7 } from "../../web/src/lib/uuid";
import { call } from "../support/api";
import { enableCabinet, setKnownCode } from "../support/db";
import { expect, test } from "../support/fixtures";
import { c, t } from "../support/i18n";

/** Адрес кабинета клиента на стенде. */
const CABINET_URL = "https://clients.e2e.test";

test("хост кабинета отдаёт кабинет и закрыт от поисковиков", async ({ page }) => {
  const response = await page.goto(CABINET_URL);

  expect(response?.headers()["x-robots-tag"]).toBe("noindex, nofollow");
  await expect(page.getByRole("heading", { name: c["signIn.phoneTitle"] })).toBeVisible();
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

test("клиент входит в кабинет по приглашению из карточки", async ({ page, owner }) => {
  const digits = `79${String(randomInt(0, 1_000_000_000)).padStart(9, "0")}`;
  const clientId = uuidv7();
  const clientName = `Иван ${clientId.slice(-6)}`;
  await call(owner.api, "clients/create", {
    id: clientId,
    name: clientName,
    gender: "MALE",
    contacts: [{ type: "PHONE", value: `+${digits}` }],
  });
  await enableCabinet(owner.companyName);

  await page.goto(`/clients/${clientId}`);
  await page.getByRole("button", { name: t["clientApp.invite"], exact: true }).click();
  await expect(page.getByText(t["clientApp.notNotified"])).toBeVisible();

  await page.goto(CABINET_URL);
  await page.getByLabel(c["signIn.phoneLabel"]).fill(`+${digits}`);
  await page.getByRole("button", { name: c["signIn.getCode"] }).click();
  await expect(page.getByLabel(c["signIn.codeLabel"])).toBeVisible();
  await setKnownCode(digits, "123456");
  await page.getByLabel(c["signIn.codeLabel"]).fill("123456");
  await page.getByRole("button", { name: c["signIn.login"] }).click();
  await page.getByRole("button", { name: c["signIn.termsAccept"] }).click();
  await expect(page.getByText(owner.companyName)).toBeVisible();
  await page.getByRole("button", { name: c["invitations.connect"] }).click();

  await expect(page.getByText(`Это кабинет клиента ${clientName}`)).toBeVisible();
});
