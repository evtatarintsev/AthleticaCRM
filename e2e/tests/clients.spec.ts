import { randomUUID } from "node:crypto";
import {
  addToGroup,
  createClient,
  createDiscipline,
  createGroup,
  createSession,
  rub,
  todayInOrg,
} from "../support/data";
import { call } from "../support/api";
import { uuidv7 } from "../../web/src/lib/uuid";
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
  await page.getByRole("link", { name: t["groups.create"] }).click();
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

test("список клиентов с абонементами совпадает с контрактом", async ({ page, owner }) => {
  const clientName = unique("Вера");
  const clientId = await createClient(owner.api, clientName);
  const groupId = await createGroup(owner.api, unique("Дневная"));
  const issue = {
    clientId,
    tariffPlanId: null,
    sessions: 8,
    groupIds: [groupId],
    durationValue: 1,
    durationUnit: "MONTHS",
    price: rub(1000),
    closeMode: "MANUAL",
  } as const;
  const variants = [
    { name: "Активный", activationMode: "IMMEDIATE" },
    { name: "Безлимит", activationMode: "IMMEDIATE", sessions: null },
    { name: "Последнее занятие", activationMode: "IMMEDIATE", sessions: 1 },
    { name: "Неделя", activationMode: "IMMEDIATE", durationValue: 7, durationUnit: "DAYS" },
    { name: "С даты", activationMode: "ON_DATE", activateOn: "2099-01-01" },
    { name: "Неактивированный", activationMode: "ON_FIRST_VISIT" },
  ] as const;
  for (const variant of variants) {
    await call(owner.api, "memberships/issue", { ...issue, ...variant, id: uuidv7() });
  }

  const { clients } = await call(owner.api, "clients/list", {});
  expect(clients.find((client) => client.id === clientId)?.memberships).toHaveLength(
    variants.length,
  );

  await page.goto("/clients");
  await expect(page.getByRole("link", { name: clientName, exact: true })).toBeVisible();
});
