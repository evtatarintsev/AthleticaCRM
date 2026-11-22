import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { AssistantDialogResponseSchema } from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, json, openApp } from "@/test/app";

/** jsdom не измеряет размеры; Radix использует observer только для скрытого input. */
beforeAll(() => {
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {
        /* В jsdom нет раскладки. */
      }
      unobserve() {
        /* В jsdom нет раскладки. */
      }
      disconnect() {
        /* В jsdom нет раскладки. */
      }
    },
  );
});
afterAll(() => {
  vi.unstubAllGlobals();
});

const clientId = "0199a0b2-7c3e-7d2a-9f10-000000000011";
const dialogId = "0199a0b2-7c3e-7d2a-9f10-000000000012";
const ready = { available: true, enabled: true, permitted: true };

/** Серверная история: fixture проходит тот же декодер, что реальные ответы. */
function conversation(
  blocks: readonly unknown[] = [],
  text = "",
  status = "completed",
  errorCode: string | null = null,
) {
  return AssistantDialogResponseSchema.parse({
    id: dialogId,
    messages:
      text === "" && blocks.length === 0
        ? []
        : [
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000013",
              role: "assistant",
              text,
              status,
              errorCode,
              blocks,
            },
          ],
  });
}

/** Серверная карточка с явными значениями по умолчанию. */
function card(status = "pending") {
  return {
    type: "client_draft",
    clientId,
    status,
    payload: {
      name: "Иванов Иван",
      gender: "MALE",
      birthday: null,
      contacts: [{ type: "PHONE", value: "89091302211", label: "мама", isPrimary: true }],
      customFields: [],
      customFieldLabels: {},
    },
  };
}

/** Открывает помощника без смены адреса страницы. */
async function openAssistant() {
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: ru["assistant.title"] }));
  await screen.findByRole("textbox", { name: ru["assistant.input"] });
  return user;
}

describe("AI-помощник", () => {
  it.each(["available", "enabled", "permitted"] as const)(
    "скрывает кнопку при %s=false",
    async (flag) => {
      const server = appServer({ "assistant/status": () => json({ ...ready, [flag]: false }) });
      openApp("/settings", server.fetch);
      await screen.findByRole("heading", { level: 1, name: ru["settings.title"] });
      await waitFor(() => {
        expect(server.to("assistant/status")).toHaveLength(1);
      });
      expect(screen.queryByRole("button", { name: ru["assistant.title"] })).toBeNull();
    },
  );

  it("сохраняет ввод при закрытии и навигации и возвращает фокус", async () => {
    const server = appServer({
      "assistant/status": () => json(ready),
      "assistant/dialogs/current": () => json(conversation()),
    });
    const { history } = openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.type(
      screen.getByRole("textbox", { name: ru["assistant.input"] }),
      "неотправленный текст",
    );
    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["assistant.title"] })).toBeNull();
    });
    expect(screen.getByRole("button", { name: ru["assistant.title"] })).toHaveFocus();
    history.push("/settings/activity-log");
    await openAssistant();
    expect(screen.getByRole("textbox", { name: ru["assistant.input"] })).toHaveValue(
      "неотправленный текст",
    );
    expect(history.location.pathname).toBe("/settings/activity-log");
  });

  it("выводит HTML и опасный URL только текстом, успех требует квитанции", async () => {
    const text = "<script>alert(1)</script> javascript:alert(1) Клиент создан";
    openApp(
      "/settings",
      appServer({
        "assistant/status": () => json(ready),
        "assistant/dialogs/current": () => json(conversation([], text)),
      }).fetch,
    );
    await openAssistant();
    expect(await screen.findByText(text)).toBeVisible();
    const log = screen.getByRole("log");
    expect(log.querySelector("script")).toBeNull();
    expect(within(log).queryByRole("link")).toBeNull();
  });

  it("подтверждает только последнюю карточку, блокирует двойной клик и восстанавливает executed", async () => {
    let dialog = conversation([card("superseded"), card()]);
    const server = appServer({
      "assistant/status": () => json(ready),
      "assistant/dialogs/current": () => json(dialog),
      "assistant/drafts/confirm": () => {
        dialog = conversation([card("superseded"), card("executed")]);
        return json({ clientId });
      },
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    expect(await screen.findAllByText("89091302211 · мама")).not.toHaveLength(0);
    await user.dblClick(await screen.findByRole("button", { name: ru["assistant.confirm"] }));
    expect(await screen.findByRole("link", { name: ru["assistant.created"] })).toHaveAttribute(
      "href",
      `/clients/${clientId}`,
    );
    expect(server.to("assistant/drafts/confirm")).toHaveLength(1);
    expect(screen.queryByRole("button", { name: ru["assistant.confirm"] })).toBeNull();
    expect(screen.getByText(ru["assistant.superseded"])).toBeVisible();
    await user.keyboard("{Escape}");
    await openAssistant();
    expect(await screen.findByRole("link", { name: ru["assistant.created"] })).toBeVisible();
    expect(screen.queryByRole("button", { name: ru["assistant.confirm"] })).toBeNull();
  });

  it.each([
    ["ASSISTANT_LIMIT", ru["assistant.error.limit"]],
    ["ASSISTANT_BUSY", ru["assistant.error.busy"]],
    ["ASSISTANT_PROVIDER_FAILURE", ru["assistant.error.provider"]],
    ["ASSISTANT_VALIDATION", ru["assistant.error.validation"]],
  ])("сохраняет текст и показывает %s без автоматической отправки", async (code, message) => {
    const server = appServer({
      "assistant/status": () => json(ready),
      "assistant/dialogs/current": () => json(conversation()),
      "assistant/messages/send": () => json({ code, message: "server", fields: null }, 400),
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.type(screen.getByRole("textbox", { name: ru["assistant.input"] }), "Иван");
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(await screen.findByText(message)).toBeVisible();
    expect(screen.getByRole("textbox", { name: ru["assistant.input"] })).toHaveValue("Иван");
    expect(server.to("assistant/messages/send")).toHaveLength(1);
  });

  it("перечитывает диалог при потере ответа, отправку не повторяет", async () => {
    let current = conversation();
    const server = appServer({
      "assistant/status": () => json(ready),
      "assistant/dialogs/current": () => json(current),
      "assistant/messages/send": () => {
        current = conversation([], "Сохранённый ответ");
        throw new Error("network lost");
      },
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.type(screen.getByRole("textbox", { name: ru["assistant.input"] }), "Иван");
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(await screen.findByText("Сохранённый ответ")).toBeVisible();
    expect(server.to("assistant/messages/send")).toHaveLength(1);
    expect(server.to("assistant/dialogs/current").length).toBeGreaterThan(1);
  });

  it("закрывает панель и скрывает кнопку после выключения", async () => {
    let enabled = true;
    const server = appServer({
      "assistant/status": () => json({ ...ready, enabled }),
      "assistant/dialogs/current": () => json(conversation()),
      "assistant/messages/send": () => {
        enabled = false;
        return json({ code: "ASSISTANT_DISABLED", message: "disabled", fields: null }, 400);
      },
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.type(screen.getByRole("textbox", { name: ru["assistant.input"] }), "Иван");
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["assistant.title"] })).toBeNull();
    });
    expect(screen.queryByRole("button", { name: ru["assistant.title"] })).toBeNull();
  });
});

describe("настройки AI-помощника", () => {
  it("владелец меняет включение и положительный лимит", async () => {
    const server = appServer({
      "assistant/settings": () =>
        json({ available: true, enabled: false, dailyMessageLimit: 100, acceptedMessages: 12 }),
      "assistant/settings/update": () =>
        json({ available: true, enabled: true, dailyMessageLimit: 50, acceptedMessages: 12 }),
    });
    openApp("/settings?panel=assistant", server.fetch);
    const user = userEvent.setup();
    const limit = await screen.findByRole("spinbutton", { name: ru["assistant.dailyLimit"] });
    await user.click(screen.getByRole("switch", { name: ru["assistant.enabled"] }));
    fireEvent.change(limit, { target: { value: "0" } });
    expect(screen.getByRole("button", { name: ru["action.save"] })).toBeDisabled();
    fireEvent.change(limit, { target: { value: "50" } });
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));
    await waitFor(() => {
      expect(server.to("assistant/settings/update")).toHaveLength(1);
    });
    expect(server.to("assistant/settings/update")[0]?.body).toEqual({
      enabled: true,
      dailyMessageLimit: 50,
    });
  });

  it("не показывает сотруднику элементы управления", async () => {
    openApp(
      "/settings?panel=assistant",
      appServer({
        "assistant/settings": () =>
          json({ code: "ASSISTANT_FORBIDDEN", message: "forbidden", fields: null }, 400),
      }).fetch,
    );
    expect(await screen.findByText(ru["assistant.error.forbidden"])).toBeVisible();
    expect(screen.queryByRole("switch")).toBeNull();
    expect(screen.queryByRole("spinbutton")).toBeNull();
  });

  it("без подключения запрещает включение и не содержит ключа и модели", async () => {
    openApp(
      "/settings?panel=assistant",
      appServer({
        "assistant/settings": () =>
          json({ available: false, enabled: false, dailyMessageLimit: 100, acceptedMessages: 0 }),
      }).fetch,
    );
    expect(await screen.findByRole("switch", { name: ru["assistant.enabled"] })).toBeDisabled();
    expect(screen.getByText(ru["assistant.error.unavailable"])).toBeVisible();
    expect(screen.queryByLabelText(/^(api.?key|model|ключ провайдера|модель)$/i)).toBeNull();
  });
});

describe("ожидание отправки", () => {
  it("блокирует повторную отправку до завершения первого запроса", async () => {
    let release: (() => void) | undefined;
    const wait = new Promise<void>((resolve) => {
      release = resolve;
    });
    let sends = 0;
    const server = appServer({
      "assistant/status": () => json(ready),
      "assistant/dialogs/current": () => json(conversation()),
      "assistant/messages/send": () => json(conversation([], "Ответ получен")),
    });
    openApp("/settings", async (input, init) => {
      if (input.endsWith("/assistant/messages/send")) {
        sends++;
        await wait;
      }
      return server.fetch(input, init);
    });
    const user = await openAssistant();
    await user.type(screen.getByRole("textbox", { name: ru["assistant.input"] }), "Иван");
    await user.dblClick(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(sends).toBe(1);
    expect(screen.getByRole("button", { name: ru["assistant.send"] })).toBeDisabled();
    release?.();
    expect(await screen.findByText("Ответ получен")).toBeVisible();
    expect(screen.getByRole("textbox", { name: ru["assistant.input"] })).toHaveValue("");
  });
});
