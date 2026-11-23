import { randomUUID } from "node:crypto";
import { request as playwrightRequest, type Page } from "@playwright/test";
import { expect, logIn, OWNER_PASSWORD, test, uniqueEmail } from "../support/fixtures";
import { t } from "../support/i18n";
import { mailFor } from "../support/mailbox";

/** Кнопка меню учётной записи: видна только в кабинете. */
function accountMenu(page: Page) {
  return page.getByRole("button", { name: t["account.menu"] });
}

/** Принимает ли сервер пару [email]/[password]: шаг выбора филиала проверяет пароль. */
async function credentialsAccepted(email: string, password: string): Promise<boolean> {
  const context = await playwrightRequest.newContext({
    baseURL: "https://app.e2e.test",
    ignoreHTTPSErrors: true,
  });
  const response = await context.post("/api/auth/branches", {
    data: { username: email, password },
  });
  await context.dispose();
  return response.ok();
}

test("регистрация через форму ведёт в кабинет новой организации", async ({ page }) => {
  const companyName = `Клуб ${randomUUID().slice(0, 8)}`;
  await page.goto("/sign-up");

  await page.getByLabel(t["auth.orgName"]).fill(companyName);
  await page.getByLabel(t["auth.yourName"]).fill("Новый владелец");
  await page.getByLabel(t["auth.email"]).fill(uniqueEmail("signup"));
  await page.getByLabel(t["auth.password"], { exact: true }).fill(OWNER_PASSWORD);
  await page.getByRole("button", { name: t["auth.actionRegister"] }).click();

  await expect(accountMenu(page)).toBeVisible();
  await page.reload();
  await expect(accountMenu(page)).toBeVisible();
});

test("после выхода кабинет закрыт, после входа снова открыт", async ({ page, owner }) => {
  await page.goto("/clients");
  await accountMenu(page).click();
  await page.getByRole("menuitem", { name: t["account.logout"] }).click();
  await expect(page).toHaveURL(/\/login/);

  await page.goto("/clients");
  await expect(page).toHaveURL(/\/login/);

  await logIn(page, owner.email, owner.password);
  await expect(accountMenu(page)).toBeVisible();
  await page.goto("/clients");
  await expect(page.getByRole("heading", { name: t["clients.title"] })).toBeVisible();
});

test("ссылка из письма подтверждает email", async ({ page, owner, request }) => {
  await page.goto("/");
  const resend = page.getByRole("button", { name: t["emailVerification.resend"] });
  await expect(resend).toBeVisible();

  const link = await mailFor(
    request,
    owner.email,
    /(https:\/\/app\.e2e\.test\/confirm-email#token=\S+)/,
  );
  await page.goto(link);
  await page.getByRole("button", { name: t["emailVerification.confirm"] }).click();
  await expect(page.getByRole("status")).toHaveText(t["emailVerification.confirmed"]);

  await page.goto("/");
  await expect(accountMenu(page)).toBeVisible();
  await expect(resend).toBeHidden();
});

test("сброс пароля по ссылке из письма", async ({ browser, owner, request }) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const newPassword = "E2e-new-password-2";

  await page.goto("/forgot-password");
  await page.getByLabel(t["auth.email"]).fill(owner.email);
  await page.getByRole("button", { name: t["passwordReset.send"] }).click();
  await expect(page.getByRole("status")).toBeVisible();

  const link = await mailFor(
    request,
    owner.email,
    /(https:\/\/app\.e2e\.test\/reset-password#token=\S+)/,
  );
  await page.goto(link);
  await page.getByLabel(t["password.new"], { exact: true }).fill(newPassword);
  await page.getByLabel(t["password.confirm"], { exact: true }).fill(newPassword);
  await page.getByRole("button", { name: t["passwordReset.submit"] }).click();
  await expect(page.getByRole("status")).toHaveText(t["passwordReset.done"]);

  expect(await credentialsAccepted(owner.email, owner.password)).toBe(false);
  expect(await credentialsAccepted(owner.email, newPassword)).toBe(true);
  await context.close();
});
