import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterAll, beforeAll, describe, expect, it, vi } from "vitest";
import { AssistantDialogResponseSchema } from "@/api/generated/contracts";
import { en } from "@/i18n/en";
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

const dialogId = "0199a0b2-7c3e-7d2a-9f10-000000000012";
const draftId = "0199a0b2-7c3e-7d2a-9f10-000000000021";
const existingId = "0199a0b2-7c3e-7d2a-9f10-000000000022";
const ready = { available: true, enabled: true, permitted: true };

/** Диалог из сообщения пользователя с файлом и ответа с [blocks]; проходит декодер ответов. */
function conversation(blocks: readonly unknown[] = [], attachments: readonly string[] = []) {
  return AssistantDialogResponseSchema.parse({
    id: dialogId,
    messages:
      blocks.length === 0 && attachments.length === 0
        ? []
        : [
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000013",
              role: "user",
              text: "",
              status: "completed",
              errorCode: null,
              blocks: [],
              attachments,
            },
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000014",
              role: "assistant",
              text: "Проверьте карточку",
              status: "completed",
              errorCode: null,
              blocks,
              attachments: [],
            },
          ],
  });
}

/** Строка карточки с позицией в файле. */
function row(n: number, status: string, extra: Record<string, unknown> = {}) {
  return {
    row: n,
    sheet: "Клиенты",
    photo: null,
    line: n,
    status,
    name: `Клиент ${String(n)}`,
    client:
      status === "error"
        ? null
        : {
            name: `Клиент ${String(n)}`,
            gender: "FEMALE",
            birthday: null,
            contacts: [],
            customFields: [],
            customFieldLabels: {},
          },
    error: null,
    errorField: null,
    warnings: [],
    uncertainFields: [],
    existingClientId: null,
    ...extra,
  };
}

/** Пакетная карточка: 214 строк, 205 готовых, 3 существующих, 6 ошибок. */
function importCard(status = "pending") {
  return {
    type: "import_draft",
    draftId,
    status,
    counts: {
      total: 214,
      ready: 205,
      exists: 3,
      duplicateInFile: 0,
      possibleDuplicate: 0,
      error: 6,
      warnings: 0,
    },
    problems: [
      row(3, "exists", { existingClientId: existingId }),
      row(12, "error", { error: "invalid_contact", errorField: "EMAIL" }),
      row(40, "ready", {
        photo: 2,
        sheet: null,
        line: 4,
        warnings: ["uncertain"],
        uncertainFields: ["name"],
      }),
    ],
    preview: [row(4, "ready"), row(5, "ready")],
    customFieldLabels: {},
  };
}

/** Открывает помощника; [locale] — язык интерфейса. */
async function openAssistant(locale: "ru" | "en" = "ru") {
  const user = userEvent.setup({ applyAccept: false });
  const dict = locale === "ru" ? ru : en;
  await user.click(await screen.findByRole("button", { name: dict["assistant.title"] }));
  await screen.findByRole("textbox", { name: dict["assistant.input"] });
  return user;
}

function file(name: string, type: string, size = 4) {
  return new File([new Uint8Array(size)], name, { type });
}

/** Сервер с загрузкой: каждый файл получает свой ID. */
function uploadingServer(handlers: Parameters<typeof appServer>[0]) {
  let uploads = 0;
  return appServer({
    "assistant/status": () => json(ready),
    "assistant/dialogs/current": () => json(conversation()),
    upload: () => {
      uploads++;
      return json({
        id: `0199a0b2-7c3e-7d2a-9f10-0000000001${String(uploads).padStart(2, "0")}`,
        url: "https://s3/file",
        originalName: "f",
        contentType: "application/octet-stream",
        sizeBytes: 4,
      });
    },
    ...handlers,
  });
}

describe("импорт через AI-помощника", () => {
  it("перетаскивание xlsx: отправка без текста с ID загрузки", async () => {
    const server = uploadingServer({
      "assistant/messages/send": () => json(conversation([importCard()], ["clients.xlsx"])),
    });
    openApp("/settings", server.fetch);
    await openAssistant();
    const input = screen.getByRole("textbox", { name: ru["assistant.input"] });
    const form = input.closest("form");
    if (form === null) {
      throw new Error("нет формы");
    }
    fireEvent.drop(form, { dataTransfer: { files: [file("clients.xlsx", "")] } });
    const attached = await screen.findByRole("list", { name: ru["assistant.attachments"] });
    expect(within(attached).getByText("clients.xlsx")).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(await screen.findByRole("button", { name: "Создать 205 клиентов" })).toBeVisible();
    expect(server.to("upload")).toHaveLength(1);
    expect(server.to("assistant/messages/send")[0]?.body).toEqual({
      dialogId,
      text: "",
      attachments: ["0199a0b2-7c3e-7d2a-9f10-000000000101"],
    });
    const log = screen.getByRole("log");
    expect(within(log).getByText("clients.xlsx")).toBeVisible();
    expect(
      within(
        screen.getByRole("textbox", { name: ru["assistant.input"] }).closest("form") ?? log,
      ).queryByText("clients.xlsx"),
    ).toBeNull();
  });

  it.each([
    [
      "12 фото",
      Array.from({ length: 12 }, (_, i) => file(`${String(i)}.jpg`, "image/jpeg")),
      ru["assistant.attachError.tooManyPhotos"],
    ],
    [
      "смешанный набор",
      [file("a.csv", "text/csv"), file("b.png", "image/png")],
      ru["assistant.attachError.mixed"],
    ],
    ["pdf", [file("a.pdf", "application/pdf")], ru["assistant.attachError.unsupported"]],
    [
      "больше 10 МБ",
      [file("a.csv", "text/csv", 10 * 1024 * 1024 + 1)],
      ru["assistant.attachError.tooBig"],
    ],
  ])("%s: ошибка до отправки", async (_, files, message) => {
    const server = uploadingServer({});
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.upload(screen.getByLabelText(ru["assistant.attach"]), files);
    expect(await screen.findByText(message)).toBeVisible();
    expect(screen.getByRole("button", { name: ru["assistant.send"] })).toBeDisabled();
    expect(server.to("upload")).toHaveLength(0);
  });

  it("распознавание недоступно: причина показана, фото остаются", async () => {
    const server = uploadingServer({
      "assistant/messages/send": () =>
        json(
          { code: "ASSISTANT_IMPORT_RECOGNITION_UNAVAILABLE", message: "server", fields: null },
          400,
        ),
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.upload(screen.getByLabelText(ru["assistant.attach"]), [
      file("p1.jpg", "image/jpeg"),
      file("p2.jpg", "image/jpeg"),
    ]);
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(
      await screen.findByText(ru["assistant.error.importRecognitionUnavailable"]),
    ).toBeVisible();
    const attached = screen.getByRole("list", { name: ru["assistant.attachments"] });
    expect(within(attached).getByText("p1.jpg")).toBeVisible();
    expect(within(attached).getByText("p2.jpg")).toBeVisible();
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    await waitFor(() => {
      expect(server.to("assistant/messages/send")).toHaveLength(2);
    });
    expect(server.to("upload")).toHaveLength(2);
    await user.click(
      screen.getByRole("button", {
        name: ru["assistant.removeAttachment"].replace("{name}", "p1.jpg"),
      }),
    );
    expect(within(attached).queryByText("p1.jpg")).toBeNull();
  });

  it("потеря ответа: диалог перечитан, файл не отправлен повторно", async () => {
    let current = conversation();
    const server = uploadingServer({
      "assistant/dialogs/current": () => json(current),
      "assistant/messages/send": () => {
        current = conversation([importCard()], ["base.csv"]);
        throw new Error("network lost");
      },
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.upload(screen.getByLabelText(ru["assistant.attach"]), [
      file("base.csv", "text/csv"),
    ]);
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(await screen.findByRole("button", { name: "Создать 205 клиентов" })).toBeVisible();
    expect(server.to("upload")).toHaveLength(1);
    expect(server.to("assistant/messages/send")).toHaveLength(1);
    await waitFor(() => {
      expect(
        screen.queryByRole("button", {
          name: ru["assistant.removeAttachment"].replace("{name}", "base.csv"),
        }),
      ).toBeNull();
    });
  });

  it("сбой без ответа, ход не принят: повтор загружает файл заново", async () => {
    let sends = 0;
    const server = uploadingServer({
      "assistant/messages/send": () => {
        sends += 1;
        if (sends === 1) {
          throw new Error("network lost");
        }
        return json(conversation([importCard()], ["base.csv"]));
      },
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.upload(screen.getByLabelText(ru["assistant.attach"]), [
      file("base.csv", "text/csv"),
    ]);
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    const attached = await screen.findByRole("list", { name: ru["assistant.attachments"] });
    expect(within(attached).getByText("base.csv")).toBeVisible();
    await user.click(screen.getByRole("button", { name: ru["assistant.send"] }));
    expect(await screen.findByRole("button", { name: "Создать 205 клиентов" })).toBeVisible();
    expect(server.to("upload")).toHaveLength(2);
    expect(server.to("assistant/messages/send")).toHaveLength(2);
  });

  it("карточка: счётчики, позиции, причины, ссылка на клиента; двойной клик создаёт один пакет", async () => {
    let dialog = conversation([importCard("superseded"), importCard()]);
    const server = uploadingServer({
      "assistant/dialogs/current": () => json(dialog),
      "assistant/imports/confirm": () => {
        dialog = conversation([importCard("superseded"), importCard("executed")]);
        return json({ created: 205 });
      },
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    const problems = (
      await screen.findAllByRole("list", { name: ru["assistant.import.problems"] })
    )[1];
    if (problems === undefined) {
      throw new Error("нет списка");
    }
    expect(within(problems).getByText("Клиенты, строка 12")).toBeVisible();
    expect(within(problems).getByText(/Некорректный контакт/)).toBeVisible();
    expect(within(problems).getByText("Фото 2, строка 4")).toBeVisible();
    expect(within(problems).getByText("Клиент 40 (?)")).toBeVisible();
    expect(
      within(problems).getByRole("link", { name: ru["assistant.import.openClient"] }),
    ).toHaveAttribute("href", `/clients/${existingId}`);
    expect(screen.getAllByText(ru["assistant.superseded"])).toHaveLength(1);
    await user.dblClick(screen.getByRole("button", { name: "Создать 205 клиентов" }));
    expect(await screen.findByText("Создано 205 клиентов")).toBeVisible();
    expect(server.to("assistant/imports/confirm")).toHaveLength(1);
    expect(server.to("assistant/imports/confirm")[0]?.body).toEqual({ draftId });
    expect(screen.getByRole("link", { name: ru["assistant.import.openClients"] })).toHaveAttribute(
      "href",
      "/clients",
    );
    expect(screen.queryByRole("button", { name: "Создать 205 клиентов" })).toBeNull();
  });

  it("устаревшая карточка остаётся неисполненной и показывает причину", async () => {
    const server = uploadingServer({
      "assistant/dialogs/current": () => json(conversation([importCard()])),
      "assistant/imports/confirm": () =>
        json({ code: "ASSISTANT_IMPORT_DRAFT_STALE", message: "stale", fields: null }, 400),
    });
    openApp("/settings", server.fetch);
    const user = await openAssistant();
    await user.click(await screen.findByRole("button", { name: "Создать 205 клиентов" }));
    expect(await screen.findByText(ru["assistant.import.stale"])).toBeVisible();
    expect(screen.getByRole("button", { name: "Создать 205 клиентов" })).toBeEnabled();
  });

  it("английский интерфейс: карточка, статусы и ошибки", async () => {
    openApp(
      "/settings",
      uploadingServer({ "assistant/dialogs/current": () => json(conversation([importCard()])) })
        .fetch,
      undefined,
      "en",
    );
    await openAssistant("en");
    expect(await screen.findByRole("button", { name: "Create 205 clients" })).toBeVisible();
    expect(screen.getByText(en["assistant.import.title"])).toBeVisible();
    expect(screen.getByText(/Invalid contact/)).toBeVisible();
    expect(screen.getByText(en["assistant.import.status.exists"], { exact: false })).toBeVisible();
    expect(screen.getByText("Клиенты, row 12")).toBeVisible();
  });

  it("пустой диалог подсказывает про таблицы и фото", async () => {
    openApp("/settings", uploadingServer({}).fetch);
    await openAssistant();
    await waitFor(() => {
      expect(screen.getByText(ru["assistant.empty"])).toBeVisible();
    });
    expect(ru["assistant.empty"]).toContain("фото журнала");
  });
});
