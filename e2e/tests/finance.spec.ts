import { randomUUID } from "node:crypto";
import {
  addToGroup,
  createCashAccount,
  createClient,
  createGroup,
  createTariff,
} from "../support/data";
import { expect, test } from "../support/fixtures";
import { t } from "../support/i18n";

test("продажа абонемента без оплаты даёт долг, оплата полной суммы его гасит", async ({
  page,
  owner,
}) => {
  const suffix = randomUUID().slice(0, 6);
  const tariff = `Месяц ${suffix}`;
  const cash = `Касса ${suffix}`;
  const clientId = await createClient(owner.api, `Вера ${suffix}`);
  await addToGroup(owner.api, clientId, await createGroup(owner.api, `Группа ${suffix}`));
  await createTariff(owner.api, tariff, 1000);
  await createCashAccount(owner.api, cash);

  await page.goto(`/clients/${clientId}`);
  const balance = page.getByRole("button", { name: new RegExp(`^${t["clientCard.balance"]}:`) });
  await expect(balance).toHaveAccessibleName(/0,00/);

  await page.getByRole("button", { name: t["clients.detail.issueSubscription"] }).first().click();
  const issue = page.getByRole("dialog", { name: t["clients.detail.issueSubscription"] });
  await issue.getByRole("button", { name: new RegExp(`^${tariff}`) }).click();
  await issue.getByRole("checkbox", { name: t["finance.payNow"] }).uncheck();
  await issue.getByRole("button", { name: t["clients.issue.submit"] }).click();
  await expect(issue).toBeHidden();
  await expect(balance).toHaveAccessibleName(/-1\s000,00/);

  await balance.click();
  const sheet = page.getByRole("dialog", { name: t["clientCard.balance"] });
  const debt = sheet.getByText(new RegExp(`${t["finance.debt"]}:`));
  await expect(debt).toContainText(/1\s000,00/);
  await sheet.getByRole("button", { name: t["finance.payment"] }).click();
  const payment = page.getByRole("dialog", { name: t["finance.payment"] });
  await payment.getByLabel(t["finance.cashAccount"]).selectOption({ label: cash });
  await payment.getByLabel(t["finance.amount"]).fill("1000");
  await payment.getByRole("button", { name: t["action.save"] }).click();
  await expect(payment).toBeHidden();

  await expect(debt).toContainText(new RegExp(`${t["finance.debt"]}: 0,00`));
});
