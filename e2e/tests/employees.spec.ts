import { randomUUID } from "node:crypto";
import { createEmployee } from "../support/data";
import { expect, logIn, test, uniqueEmail } from "../support/fixtures";
import { t } from "../support/i18n";
import { mailFor } from "../support/mailbox";

test("сотрудник без прав на финансы входит по паролю из письма и не видит финансов", async ({
  page,
  browser,
  owner,
}) => {
  const name = `Тренер ${randomUUID().slice(0, 6)}`;
  const email = uniqueEmail("coach");
  await createEmployee(owner.api, name, email);

  await page.goto("/employees");
  await page
    .getByRole("button", { name: t["employees.sendAccessFor"].replace("{name}", name) })
    .click();
  const dialog = page.getByRole("dialog", { name: t["employees.sendAccess"] });
  await dialog.getByLabel(t["employees.sendAccessEmail"]).fill(email);
  await dialog.getByLabel(t["employees.sendAccessPassword"], { exact: true }).fill("Coach-pass-1");
  await dialog.getByRole("button", { name: t["employees.sendAccess"] }).click();
  await expect(dialog).toBeHidden();

  const password = await mailFor(owner.api, email, /Пароль: (\S+)/);
  const coachContext = await browser.newContext();
  const coach = await coachContext.newPage();
  await coach.goto("/login");
  await logIn(coach, email, password);

  const navigation = coach.getByRole("navigation", { name: "Разделы" });
  await expect(navigation.getByRole("link", { name: t["nav.clients"] })).toBeVisible();
  await expect(navigation.getByRole("link", { name: t["nav.finance"] })).toHaveCount(0);
  await coach.goto("/finance");
  await expect(coach.getByText(t["finance.noAccess"])).toBeVisible();
  await coachContext.close();
});
