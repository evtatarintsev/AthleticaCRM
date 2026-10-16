import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  ClientDetailResponseSchema,
  CreateTariffPlanRequestSchema,
  IssueMembershipRequestSchema,
  UpdateTariffPlanRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp } from "@/test/app";

const dana = ClientDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000504",
  name: "Дана Лебедева",
  avatarId: null,
  birthday: null,
  gender: "FEMALE",
  groups: [
    { id: "0199a0b2-7c3e-7d2a-9f10-000000000901", name: "Утренняя группа" },
    { id: "0199a0b2-7c3e-7d2a-9f10-000000000902", name: "Вечерняя группа" },
  ],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [],
  state: "ACTIVE",
});

const monthly = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000201",
  name: "Месяц",
  sessions: 8,
  durationValue: 1,
  durationUnit: "MONTHS",
  price: { minorUnits: 400000, currency: "RUB" },
  archived: false,
};
const unlimited = {
  ...monthly,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000202",
  name: "Безлимит",
  sessions: null,
  price: { minorUnits: 750000, currency: "RUB" },
};
const summer = {
  ...monthly,
  id: "0199a0b2-7c3e-7d2a-9f10-000000000203",
  name: "Летний",
  archived: true,
};

/**
 * Сервер карточки клиента с тарифами в памяти; валюта организации — рубль. Список тарифов, как
 * и настоящий сервер, без `includeArchived` архивные не отдаёт. [failIssue] — выдача отклоняется.
 */
function issueServer({ failIssue = false }: { failIssue?: boolean } = {}) {
  let tariffs: readonly Record<string, unknown>[] = [monthly, unlimited, summer];
  return appServer({
    "custom-fields/list": () => json([]),
    "org/settings": () => json({ name: "Лига", timezone: "Europe/Moscow", currency: "RUB" }),
    "clients/detail": () => json(dana),
    "clients/notes/list": () => json({ notes: [] }),
    "memberships/list": () => json({ memberships: [] }),
    "groups/list": () =>
      json({
        groups: [
          {
            id: "0199a0b2-7c3e-7d2a-9f10-000000000903",
            name: "Дневная группа",
            schedule: [],
            scheduleChangeAt: null,
            employees: [],
          },
        ],
        total: 1,
      }),
    "tariffs/list": () => json({ tariffs: tariffs.filter((t) => t["archived"] !== true) }),
    "tariffs/create": ({ body }) => {
      tariffs = [...tariffs, { ...CreateTariffPlanRequestSchema.parse(body), archived: false }];
      return empty();
    },
    "tariffs/update": ({ body }) => {
      const tariff = UpdateTariffPlanRequestSchema.parse(body);
      tariffs = tariffs.map((t) => (t["id"] === tariff.id ? { ...t, ...tariff } : t));
      return empty();
    },
    "memberships/issue": () =>
      failIssue
        ? json({ code: "MEMBERSHIP_CONFLICT", message: "Абонемент не выдан" }, 409)
        : empty(),
  });
}

const PRICE = ru["clients.detail.subscriptionPrice"].replace("{currency}", "RUB");

/** Открывает карточку Даны и панель выдачи; возвращает панель и пользователя. */
async function openIssue(api: ReturnType<typeof issueServer>) {
  openApp(`/clients/${dana.id}`, api.fetch);
  const user = userEvent.setup();
  await user.click(
    await screen.findByRole("button", { name: ru["clients.detail.issueSubscription"] }),
  );
  const dialog = await screen.findByRole("dialog", {
    name: ru["clients.detail.issueSubscription"],
  });
  return { user, dialog, panel: within(dialog) };
}

/** Кнопка выбора тарифа [name] на первом шаге панели [dialog]. */
const pick = (dialog: HTMLElement, name: string) =>
  within(dialog).findByRole("button", { name: new RegExp(`^${name}`) });

describe("выдача абонемента", () => {
  it("показывает активные тарифы и выдаёт по тарифу с изменённой стоимостью", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    expect(await pick(dialog, "Месяц")).toHaveTextContent("8 занятий · 1 мес.");
    expect(panel.queryByRole("button", { name: /^Летний/ })).toBeNull();

    await user.click(await pick(dialog, "Месяц"));
    expect(panel.getByLabelText(ru["clients.detail.subscriptionSessionsField"])).toHaveValue("8");
    await user.clear(panel.getByLabelText(PRICE));
    await user.type(panel.getByLabelText(PRICE), "4800");
    await user.click(panel.getByRole("button", { name: ru["clients.issue.submit"] }));

    await waitFor(() => {
      expect(api.to("memberships/issue")).toHaveLength(1);
    });
    const request = IssueMembershipRequestSchema.parse(api.to("memberships/issue").at(-1)?.body);
    expect(request.tariffPlanId).toBe(monthly.id);
    expect(request.name).toBe("Месяц");
    expect(request.sessions).toBe(8);
    expect(request.price).toEqual({ minorUnits: 480000, currency: "RUB" });
    expect(request.activationMode).toBe("IMMEDIATE");
    expect(request.groupIds).toEqual(dana.groups.map((group) => group.id));
    expect(request.closeMode).toBe("MANUAL");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
  });

  it("безлимитный тариф заполняет форму без числа занятий", async () => {
    const { user, dialog, panel } = await openIssue(issueServer());

    await user.click(await pick(dialog, "Безлимит"));

    expect(panel.getByLabelText(ru["clients.detail.subscriptionUnlimitedLabel"])).toBeChecked();
    expect(panel.queryByLabelText(ru["clients.detail.subscriptionSessionsField"])).toBeNull();
    expect(panel.getByLabelText(PRICE)).toHaveValue("7500");
  });

  it("не отправляет форму с ошибками", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    await user.click(await pick(dialog, ru["clients.detail.subscriptionIndividual"]));
    await user.click(panel.getByRole("button", { name: ru["clients.issue.submit"] }));

    expect(await panel.findByText(ru["error.invalidAmount"])).toBeVisible();
    expect(api.to("memberships/issue")).toHaveLength(0);
  });

  it("при ошибке сервера остаётся на втором шаге с введёнными значениями", async () => {
    const { user, dialog, panel } = await openIssue(issueServer({ failIssue: true }));

    await user.click(await pick(dialog, "Месяц"));
    await user.clear(panel.getByLabelText(PRICE));
    await user.type(panel.getByLabelText(PRICE), "4800");
    await user.click(panel.getByRole("button", { name: ru["clients.issue.submit"] }));

    expect(await panel.findByRole("alert")).toBeVisible();
    expect(panel.getByLabelText(PRICE)).toHaveValue("4800");
  });

  it("«Сменить» и тот же тариф сохраняют правки, другой тариф заполняет поля заново", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    await user.click(await pick(dialog, "Месяц"));
    await user.clear(panel.getByLabelText(PRICE));
    await user.type(panel.getByLabelText(PRICE), "4800");
    await user.click(panel.getByRole("button", { name: ru["clients.issue.change"] }));
    expect(await pick(dialog, "Месяц")).toHaveAttribute("aria-current", "true");
    await user.click(await pick(dialog, "Месяц"));
    expect(panel.getByLabelText(PRICE)).toHaveValue("4800");

    await user.click(panel.getByRole("button", { name: ru["clients.issue.change"] }));
    await user.click(await pick(dialog, "Безлимит"));
    expect(panel.getByLabelText(PRICE)).toHaveValue("7500");
    expect(api.to("memberships/issue")).toHaveLength(0);
  });

  it("созданный тариф сразу выбирается", async () => {
    const api = issueServer();
    const { user, panel } = await openIssue(api);

    await user.click(await panel.findByRole("button", { name: ru["tariffs.create"] }));
    const editor = within(await screen.findByRole("dialog", { name: ru["tariffs.create"] }));
    await user.type(editor.getByLabelText(ru["tariffs.name"]), "12 занятий");
    await user.type(editor.getByLabelText(ru["tariffs.sessions"]), "12");
    await user.clear(editor.getByLabelText(ru["tariffs.duration"]));
    await user.type(editor.getByLabelText(ru["tariffs.duration"]), "2");
    await user.type(editor.getByLabelText("Стоимость, ₽"), "6000");
    await user.click(editor.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["tariffs.create"] })).toBeNull();
    });
    expect(await panel.findByLabelText(PRICE)).toHaveValue("6000");
    expect(panel.getByLabelText(ru["clients.detail.subscriptionSessionsField"])).toHaveValue("12");
    expect(panel.getByLabelText(ru["clients.detail.subscriptionDuration"])).toHaveValue("2");
    await user.click(panel.getByRole("button", { name: ru["clients.issue.submit"] }));

    await waitFor(() => {
      expect(api.to("memberships/issue")).toHaveLength(1);
    });
    const created = CreateTariffPlanRequestSchema.parse(api.to("tariffs/create").at(-1)?.body);
    const request = IssueMembershipRequestSchema.parse(api.to("memberships/issue").at(-1)?.body);
    expect(request.tariffPlanId).toBe(created.id);
    expect(request.name).toBe("12 занятий");
  });

  it("изменение тарифа не выбирает его и обновляет строку", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    await user.click(await panel.findByRole("button", { name: "Изменить «Месяц»" }));
    const editor = within(await screen.findByRole("dialog", { name: ru["tariffs.edit"] }));
    expect(editor.queryByRole("button", { name: ru["tariffs.archive"] })).toBeNull();
    await user.clear(editor.getByLabelText("Стоимость, ₽"));
    await user.type(editor.getByLabelText("Стоимость, ₽"), "4500");
    await user.click(editor.getByRole("button", { name: ru["action.save"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["tariffs.edit"] })).toBeNull();
    });
    await waitFor(async () => {
      expect((await pick(dialog, "Месяц")).textContent.replace(/\s/g, " ")).toContain("4 500,00 ₽");
    });
    expect(await pick(dialog, "Месяц")).not.toHaveAttribute("aria-current");
    expect(panel.queryByLabelText(PRICE)).toBeNull();
  });

  it("закрытие без правок не спрашивает подтверждение, с правками — спрашивает", async () => {
    const { user, dialog } = await openIssue(issueServer());

    await user.click(await pick(dialog, "Месяц"));
    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    await user.click(screen.getByRole("button", { name: ru["clients.detail.issueSubscription"] }));
    const reopenedDialog = await screen.findByRole("dialog", {
      name: ru["clients.detail.issueSubscription"],
    });
    const reopened = within(reopenedDialog);
    await user.click(await pick(reopenedDialog, "Месяц"));
    await user.type(reopened.getByLabelText(PRICE), "1");
    await user.keyboard("{Escape}");

    expect(await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeVisible();
  });
  it("режим «с даты» отправляет дату активации, «при первом посещении» — крайнюю дату", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    await user.click(await pick(dialog, "Месяц"));
    expect(panel.getByRole("radio", { name: ru["membership.activation.IMMEDIATE"] })).toBeChecked();
    await user.click(panel.getByRole("radio", { name: ru["membership.activation.ON_DATE"] }));
    const date = panel.getByLabelText(ru["clients.issue.activateOn"]);
    await user.clear(date);
    await user.type(date, "2026-10-20");
    await user.click(panel.getByRole("radio", { name: ru["membership.close.ON_EXPIRY"] }));
    await user.click(panel.getByRole("button", { name: ru["clients.issue.submit"] }));

    await waitFor(() => {
      expect(api.to("memberships/issue")).toHaveLength(1);
    });
    const request = IssueMembershipRequestSchema.parse(api.to("memberships/issue").at(-1)?.body);
    expect(request.activationMode).toBe("ON_DATE");
    expect(request.activateOn).toBe("2026-10-20");
    expect(request.activationDeadline).toBeNull();
    expect(request.closeMode).toBe("ON_EXPIRY");
  });

  it("при первом посещении без крайней даты поле даты не обязательно", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    await user.click(await pick(dialog, "Месяц"));
    await user.click(
      panel.getByRole("radio", { name: ru["membership.activation.ON_FIRST_VISIT"] }),
    );
    expect(panel.queryByLabelText(ru["clients.issue.activateOn"])).toBeNull();
    await user.click(panel.getByRole("button", { name: ru["clients.issue.submit"] }));

    await waitFor(() => {
      expect(api.to("memberships/issue")).toHaveLength(1);
    });
    const request = IssueMembershipRequestSchema.parse(api.to("memberships/issue").at(-1)?.body);
    expect(request.activationMode).toBe("ON_FIRST_VISIT");
    expect(request.activateOn).toBeNull();
    expect(request.activationDeadline).toBeNull();
  });

  it("безлимит отключает закрытие при списании последнего и сбрасывает его на «Вручную»", async () => {
    const { user, dialog, panel } = await openIssue(issueServer());

    await user.click(await pick(dialog, "Месяц"));
    const onLast = panel.getByRole("radio", { name: ru["membership.close.ON_LAST_SESSION"] });
    await user.click(onLast);
    expect(onLast).toBeChecked();

    await user.click(panel.getByLabelText(ru["clients.detail.subscriptionUnlimitedLabel"]));

    expect(onLast).toBeDisabled();
    expect(onLast).not.toBeChecked();
    expect(panel.getByRole("radio", { name: ru["membership.close.MANUAL"] })).toBeChecked();
  });
  it("группы клиента отмечены по умолчанию, остальные группы филиала — нет", async () => {
    const { user, dialog, panel } = await openIssue(issueServer());

    await user.click(await pick(dialog, "Месяц"));

    expect(panel.getByRole("checkbox", { name: "Утренняя группа" })).toBeChecked();
    expect(panel.getByRole("checkbox", { name: "Вечерняя группа" })).toBeChecked();
    expect(panel.getByRole("checkbox", { name: "Дневная группа" })).not.toBeChecked();
  });

  it("без групп выдать нельзя", async () => {
    const api = issueServer();
    const { user, dialog, panel } = await openIssue(api);

    await user.click(await pick(dialog, "Месяц"));
    await user.click(panel.getByRole("checkbox", { name: "Утренняя группа" }));
    await user.click(panel.getByRole("checkbox", { name: "Вечерняя группа" }));

    expect(panel.getByRole("button", { name: ru["clients.issue.submit"] })).toBeDisabled();
    expect(panel.getByText(ru["clients.issue.groupsRequired"])).toBeInTheDocument();
    expect(api.to("memberships/issue")).toHaveLength(0);
  });
});
