import { randomUUID } from "node:crypto";
import {
  addToGroup,
  createClient,
  createDiscipline,
  createGroup,
  createSession,
  todayInOrg,
} from "../support/data";
import { expect, test } from "../support/fixtures";
import { t } from "../support/i18n";

/** Уникальное в пределах прогона имя с префиксом [prefix]. */
function unique(prefix: string): string {
  return `${prefix} ${randomUUID().slice(0, 6)}`;
}

test("клиент, добавленный в группу, виден в группе и в карточке", async ({ page, owner }) => {
  await createDiscipline(owner.api, unique("Плавание"));
  const groupName = unique("Утренняя");
  const clientName = unique("Анна");

  await page.goto("/groups");
  await page.getByRole("link", { name: t["groups.create"] }).first().click();
  const groupSheet = page.getByRole("dialog", { name: t["groups.create"] });
  await groupSheet.getByLabel(t["groups.name"], { exact: false }).fill(groupName);
  await groupSheet.getByRole("button", { name: t["action.create"] }).click();
  await expect(groupSheet).toBeHidden();

  await page.goto("/clients/new");
  await page.getByLabel(t["clients.name"]).fill(clientName);
  await page.getByRole("button", { name: t["action.create"] }).click();
  await page.getByRole("link", { name: clientName, exact: true }).click();

  await page.getByRole("button", { name: t["clients.detail.addToGroup"] }).click();
  const panel = page.getByRole("dialog", { name: t["clients.detail.addToGroupsTitle"] });
  await panel.getByRole("checkbox", { name: groupName }).check();
  await panel.getByRole("button", { name: t["action.add"] }).click();
  await expect(panel).toBeHidden();
  await expect(page.getByText(groupName)).toBeVisible();

  await page.goto("/groups");
  await page.getByRole("link", { name: groupName, exact: true }).click();
  await page.getByRole("tab", { name: t["groups.tabs.clients"] }).click();
  await expect(page.getByText(clientName)).toBeVisible();
});

test("отметка посещения видна в занятии и в истории клиента", async ({ page, owner }) => {
  const groupName = unique("Вечерняя");
  const clientName = unique("Борис");
  const groupId = await createGroup(owner.api, groupName);
  const clientId = await createClient(owner.api, clientName);
  await addToGroup(owner.api, clientId, groupId);
  const sessionId = await createSession(owner.api, groupId, todayInOrg());

  await page.goto(`/sessions/${sessionId}`);
  const row = page.getByRole("listitem", { name: clientName });
  await row
    .getByRole("group", {
      name: t["sessionDetail.attendance.groupAria"].replace("{name}", clientName),
    })
    .getByRole("button", { name: t["sessionDetail.attendance.PRESENT"], exact: true })
    .click();
  await expect(
    row.getByRole("button", { name: t["sessionDetail.attendance.PRESENT"], exact: true }),
  ).toHaveAttribute("aria-pressed", "true");

  await page.getByRole("button", { name: t["sessionDetail.completeAction"] }).click();
  await expect(page.getByText(t["sessionDetail.completed"]).first()).toBeVisible();

  await page.goto(`/clients/${clientId}`);
  await page.getByRole("button", { name: t["clientOverview.openSessions"] }).click();
  const sessions = page.getByRole("dialog", { name: t["clientOverview.sessions"] });
  await expect(
    sessions.getByRole("button", { name: `${t["clientOverview.filter.PRESENT"]} · 1` }),
  ).toBeVisible();
  await expect(sessions.getByText(groupName)).toBeVisible();
});
