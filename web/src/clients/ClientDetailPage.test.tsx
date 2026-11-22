import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AddClientNoteRequestSchema,
  AttachClientDocRequestSchema,
  ClientDetailResponseSchema,
  ClientNoteIdSchema,
  CreateTaskRequestSchema,
  CustomFieldDefinitionSchema,
  DeleteClientDocRequestSchema,
  DeleteClientNoteRequestSchema,
  EditClientNoteRequestSchema,
  InstantSchema,
  MessageSchemaSchema,
  ScheduleListRequestSchema,
  SendMessageRequestSchema,
  TaskDetailResponseSchema,
  TaskDetailRequestSchema,
  TaskListRequestSchema,
  TaskIdSchema,
  UpdateTaskStatusRequestSchema,
  type ClientDetailResponse,
  type ClientNoteSchema,
  type CustomFieldDefinition,
  type MessageSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { addDays, todayLocalDate } from "@/lib/localDate";
import { appServer, empty, json, openApp, session } from "@/test/app";

const alice = ClientDetailResponseSchema.parse({
  createdAt: "2024-01-01T10:00:00Z",
  id: "0199a0b2-7c3e-7d2a-9f10-000000000501",
  name: "Алиса Иванова",
  avatarId: null,
  birthday: "1990-05-01",
  gender: "FEMALE",
  groups: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000901", name: "Утренняя группа" }],
  balance: { minorUnits: 0, currency: "RUB" },
  docs: [],
  leadSourceId: null,
  customFields: [],
  contacts: [
    { id: "0199a0b2-7c3e-7d2a-9f10-000000000601", type: "PHONE", value: "+70000000001" },
    { id: "0199a0b2-7c3e-7d2a-9f10-000000000602", type: "EMAIL", value: "alice@example.com" },
  ],
  state: "ACTIVE",
});

const customFieldDefs: readonly CustomFieldDefinition[] = [
  CustomFieldDefinitionSchema.parse({
    fieldType: "text",
    fieldKey: "notes",
    label: "Заметка",
    isRequired: false,
    isSearchable: false,
    isSortable: false,
    minLength: null,
    maxLength: null,
  }),
];

/** Поддельный сервер: клиент, его заметки и документы в памяти. */
/** Загруженный файл паспорта клиента. */
const passport = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000801",
  url: "https://files.example.com/passport.pdf",
  originalName: "passport.pdf",
  contentType: "application/pdf",
  sizeBytes: 1234,
};

function detailServer(
  initial: ClientDetailResponse,
  notes: readonly ClientNoteSchema[] = [],
  uploadInfo: typeof passport = passport,
  extra: Parameters<typeof appServer>[0] = {},
) {
  let client = initial;
  let currentNotes = notes;
  return appServer({
    "org/client-card": () => json({ tasks: true, notes: true, chat: true, documents: true }),
    "clients/unpaid-sessions": () => json({ sessions: [] }),
    "custom-fields/list": () => json(customFieldDefs),
    "clients/detail": () => json(client),
    "clients/archive": () => {
      client = { ...client, state: "ARCHIVED" };
      return empty();
    },
    "clients/restore": () => {
      client = { ...client, state: "ACTIVE" };
      return empty();
    },
    "clients/notes/list": () => json({ notes: currentNotes }),
    "clients/notes/add": ({ body }) => {
      const request = AddClientNoteRequestSchema.parse(body);
      const note: ClientNoteSchema = {
        id: ClientNoteIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000701"),
        text: request.text,
        author: { id: session().employeeId, name: session().name },
        createdAt: InstantSchema.parse("2024-01-01T10:00:00Z"),
        updatedAt: null,
      };
      currentNotes = [...currentNotes, note];
      return json({ notes: currentNotes });
    },
    "clients/notes/edit": ({ body }) => {
      const request = EditClientNoteRequestSchema.parse(body);
      currentNotes = currentNotes.map((note) =>
        note.id === request.noteId
          ? { ...note, text: request.text, updatedAt: InstantSchema.parse("2024-01-01T11:00:00Z") }
          : note,
      );
      return json({ notes: currentNotes });
    },
    "clients/notes/delete": ({ body }) => {
      const request = DeleteClientNoteRequestSchema.parse(body);
      currentNotes = currentNotes.filter((note) => note.id !== request.noteId);
      return json({ notes: currentNotes });
    },
    upload: () => json(passport),
    "upload/info": () => json(uploadInfo),
    "clients/docs/attach": ({ body }) => {
      const request = AttachClientDocRequestSchema.parse(body);
      client = {
        ...client,
        docs: [
          ...client.docs,
          {
            id: request.docId,
            uploadId: request.uploadId,
            name: request.name,
            createdAt: InstantSchema.parse("2024-01-01T10:00:00Z"),
          },
        ],
      };
      return empty();
    },
    ...extra,
    "clients/docs/delete": ({ body }) => {
      const request = DeleteClientDocRequestSchema.parse(body);
      client = { ...client, docs: client.docs.filter((doc) => doc.id !== request.docId) };
      return empty();
    },
  });
}

describe("карточка клиента", () => {
  it.each([false, true])("возврат виден при загрузке и после ответа (ошибка: %s)", async (fail) => {
    let release: (() => void) | undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    const server = detailServer(alice);
    const { history } = openApp(`/clients/${alice.id}`, async (input, init) => {
      if (new URL(input, "http://localhost").pathname === "/api/clients/detail") {
        await pending;
        if (fail)
          return json({ code: "CLIENT_NOT_FOUND", message: "Не найдено", fields: null }, 404);
      }
      return server.fetch(input, init);
    });
    const back = await screen.findByRole("link", { name: ru["action.back"] });
    expect(back).toBeVisible();
    expect(back).toHaveAttribute("href", "/clients");
    expect(screen.queryByRole("heading", { name: alice.name })).not.toBeInTheDocument();
    release?.();
    if (fail) await screen.findByText(ru["clients.loadClientError"]);
    else await screen.findByRole("heading", { name: alice.name });
    expect(screen.getAllByRole("link", { name: ru["action.back"] })).toHaveLength(1);
    await userEvent.setup().click(screen.getByRole("link", { name: ru["action.back"] }));
    await waitFor(() => {
      expect(history.location.pathname).toBe("/clients");
      expect(history.location.search).toBe("");
    });
  });

  it("из анкеты возвращает в список без параметров карточки", async () => {
    const { history } = openApp(`/clients/${alice.id}`, detailServer(alice).fetch);
    const user = userEvent.setup();
    await screen.findByRole("heading", { name: ru["clientCard.profile"] });
    expect(history.location.search).toBe("");
    await user.click(screen.getByRole("link", { name: ru["action.back"] }));
    await waitFor(() => {
      expect(history.location.pathname).toBe("/clients");
      expect(history.location.search).toBe("");
    });
  });

  it("показывает сведения, добавляет заметку и переносит клиента в архив", async () => {
    const api = detailServer(alice);
    openApp(`/clients/${alice.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("heading", { name: alice.name });
    expect(screen.getAllByText(alice.contacts[0]?.value ?? "")).toHaveLength(2);
    expect(screen.getByText(alice.contacts[1]?.value ?? "")).toBeInTheDocument();
    expect(screen.getByText("Утренняя группа")).toBeInTheDocument();

    await user.click(await screen.findByRole("button", { name: /^Все заметки/ }));
    await user.type(
      screen.getByLabelText(ru["clients.detail.noteFieldLabel"]),
      "Перезвонить завтра",
    );
    await user.click(screen.getByRole("button", { name: ru["action.add"] }));

    await screen.findAllByText("Перезвонить завтра");
    const addRequest = AddClientNoteRequestSchema.parse(api.to("clients/notes/add").at(-1)?.body);
    expect(addRequest.clientId).toBe(alice.id);
    expect(addRequest.text).toBe("Перезвонить завтра");

    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: ru["clientCard.action.more"] }));
    await user.click(await screen.findByRole("menuitem", { name: ru["action.archive"] }));
    await screen.findByText(ru["clients.view.archived"]);
    expect(api.to("clients/archive").at(-1)?.body).toEqual({ clientIds: [alice.id] });
    expect(screen.queryByRole("link", { name: ru["action.edit"] })).not.toBeInTheDocument();
  });

  it("редактирует и удаляет свою заметку", async () => {
    const note: ClientNoteSchema = {
      id: ClientNoteIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000702"),
      text: "исходный текст",
      author: { id: session().employeeId, name: session().name },
      createdAt: InstantSchema.parse("2024-01-01T10:00:00Z"),
      updatedAt: null,
    };
    const api = detailServer(alice, [note]);
    openApp(`/clients/${alice.id}?tab=notes`, api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: /^Все заметки/ }));
    await screen.findAllByText("исходный текст");
    await user.click(screen.getByRole("button", { name: ru["clients.detail.noteEditAria"] }));
    const field = screen.getByLabelText(ru["clients.detail.noteFieldLabel"]);
    await user.clear(field);
    await user.type(field, "новый текст");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await screen.findAllByText("новый текст");
    expect(screen.getByText(ru["clients.detail.noteEdited"])).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: ru["clients.detail.noteDeleteAria"] }));
    await user.click(screen.getByRole("button", { name: ru["action.delete"] }));

    await waitFor(() => {
      expect(screen.getByText(ru["clients.detail.noteEmpty"])).toBeInTheDocument();
    });
  });

  it("загружает и удаляет документ", async () => {
    const api = detailServer(alice);
    openApp(`/clients/${alice.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("heading", { name: ru["clientCard.profile"] });
    await screen.findByText(ru["clients.detail.noDocuments"]);
    const fileInput = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (fileInput === null) {
      throw new Error("file input not found");
    }
    const file = new File(["content"], "passport.pdf", { type: "application/pdf" });
    await user.upload(fileInput, file);

    expect(await screen.findByRole("button", { name: "Открыть «passport.pdf»" })).toBeEnabled();

    await user.click(
      screen.getByRole("button", {
        name: ru["clients.detail.deleteDoc"].replace("{name}", "passport.pdf"),
      }),
    );
    const confirm = await screen.findByRole("dialog", {
      name: ru["clients.detail.deleteDocTitle"],
    });
    await user.click(within(confirm).getByRole("button", { name: ru["action.delete"] }));

    await waitFor(() => {
      expect(screen.getByText(ru["clients.detail.noDocuments"])).toBeInTheDocument();
    });
  });

  it("документ-изображение показывается миниатюрой и открывается в просмотрщике", async () => {
    const withDoc = ClientDetailResponseSchema.parse({
      ...alice,
      docs: [
        {
          id: "0199a0b2-7c3e-7d2a-9f10-000000000901",
          uploadId: passport.id,
          name: "Справка от врача",
          createdAt: "2024-01-01T10:00:00Z",
        },
      ],
    });
    const scan = {
      ...passport,
      url: "https://files.example.com/scan.jpg",
      originalName: "scan.jpg",
      contentType: "image/jpeg",
    };
    openApp(`/clients/${alice.id}?tab=profile`, detailServer(withDoc, [], scan).fetch);
    const user = userEvent.setup();

    const tile = await screen.findByRole("button", { name: "Открыть «Справка от врача»" });
    await waitFor(() => {
      expect(tile.querySelector("img")).toHaveAttribute(
        "src",
        "https://files.example.com/scan.jpg",
      );
    });
    await user.click(tile);

    const viewer = within(
      await screen.findByRole("dialog", { name: ru["attachments.viewerTitle"] }),
    );
    expect(viewer.getByRole("img", { name: "Справка от врача" })).toBeVisible();
  });

  it("показывает дополнительные поля клиента", async () => {
    const withField = ClientDetailResponseSchema.parse({
      ...alice,
      customFields: [{ type: "text", fieldKey: "notes", value: "аллергия на пыль" }],
    });
    const api = detailServer(withField);
    openApp(`/clients/${withField.id}?tab=profile`, api.fetch);

    await screen.findByText("аллергия на пыль");
    const section = screen.getByText(ru["clients.additionalFields"]).closest("div");
    if (section === null) {
      throw new Error("section not found");
    }
    expect(within(section).getByText("Заметка")).toBeInTheDocument();
  });
  it("показывает ближайшие занятия, посещения, пропуски, задачи и предупреждения", async () => {
    const today = todayLocalDate();
    const group = alice.groups[0] ?? { id: "", name: "" };
    const api = detailServer(alice, [], passport, {
      "schedule/list": () =>
        json({
          sessions: [
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000c01",
              group,
              date: addDays(today, 1),
              startTime: "10:00:00",
              endTime: "11:00:00",
              hall: { id: "0199a0b2-7c3e-7d2a-9f10-000000000d01", name: "Большой зал" },
              coaches: [{ id: "0199a0b2-7c3e-7d2a-9f10-000000000d02", name: "Ксения" }],
              disciplines: [],
              status: "SCHEDULED",
              colorKey: "GREEN",
              coachesOverridden: false,
              rescheduledFrom: null,
              isManual: false,
            },
          ],
        }),
      "attendance/client": () =>
        json({
          items: [
            {
              sessionId: "0199a0b2-7c3e-7d2a-9f10-000000000c02",
              group: { id: group.id, name: "Посещённая группа" },
              date: addDays(today, -2),
              startTime: "10:00:00",
              presence: "PRESENT",
              labels: [],
            },
            {
              sessionId: "0199a0b2-7c3e-7d2a-9f10-000000000c03",
              group: { id: group.id, name: "Пропущенная группа" },
              date: addDays(today, -4),
              startTime: "10:00:00",
              presence: "ABSENT",
              labels: [
                {
                  id: "0199a0b2-7c3e-7d2a-9f10-000000000c04",
                  name: "Болезнь",
                  scope: "ABSENT",
                  position: 0,
                  isArchived: false,
                },
              ],
            },
          ],
        }),
      "tasks/list": () =>
        json({
          tasks: [
            {
              id: "0199a0b2-7c3e-7d2a-9f10-000000000c05",
              title: "Напомнить о продлении",
              assigneeId: null,
              assigneeName: null,
              clientId: alice.id,
              clientName: alice.name,
              status: "PENDING",
              dueDate: "2024-01-01T10:00:00Z",
              dueDateEnd: null,
            },
          ],
          total: 1,
        }),
      "memberships/list": () => json({ memberships: [] }),
    });
    openApp(`/clients/${alice.id}`, api.fetch);

    await screen.findByRole("link", { name: /Открыть занятие: Утренняя группа/ });
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole("button", { name: ru["clientOverview.openSessions"] }),
    );
    expect(await screen.findAllByText(/Посещённая группа/)).toHaveLength(1);
    expect(screen.getByText(/Пропущенная группа/)).toBeInTheDocument();
    expect(screen.getByText("Болезнь")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(
      await screen.findByRole("button", { name: "Напомнить о продлении" }),
    ).toBeInTheDocument();

    const alerts = within(
      screen.getByRole("heading", { name: ru["clientOverview.attention"] }).closest("section") ??
        document.body,
    );
    expect(alerts.getByText(ru["clientCard.alert.noActiveSubscription"])).toBeInTheDocument();
    expect(alerts.getByText(/1 просроченная задача/)).toBeInTheDocument();

    const schedule = ScheduleListRequestSchema.parse(api.to("schedule/list").at(-1)?.body);
    expect(schedule.groupIds).toEqual([group.id]);
  });
});

/** Задача клиента «Алиса» в карточке задачи. */
const aliceTask = TaskDetailResponseSchema.parse({
  id: "0199a0b2-7c3e-7d2a-9f10-000000000e01",
  createdBy: session().employeeId,
  createdByName: session().name,
  assigneeId: null,
  assigneeName: null,
  clientId: alice.id,
  clientName: alice.name,
  title: "Позвонить Алисе",
  description: "Уточнить расписание",
  status: "PENDING",
  dueDate: null,
  dueDateEnd: null,
  completedAt: null,
  createdAt: "2026-01-01T00:00:00Z",
  attachments: [],
});

/** Сервер карточки клиента с задачами клиента в памяти; создание добавляет задачу в список. */
function tasksServer() {
  let tasks: readonly (typeof aliceTask)[] = [aliceTask];
  return detailServer(alice, [], passport, {
    "tasks/list": () =>
      json({
        tasks: tasks.map((task) => ({
          id: task.id,
          title: task.title,
          assigneeId: task.assigneeId,
          assigneeName: task.assigneeName,
          clientId: task.clientId,
          clientName: task.clientName,
          status: task.status,
          dueDate: task.dueDate,
          dueDateEnd: task.dueDateEnd,
        })),
        total: tasks.length,
      }),
    "tasks/detail": () => json(aliceTask),
    "employees/list": () => json({ employees: [], total: 0 }),
    "tasks/create": ({ body }) => {
      const request = CreateTaskRequestSchema.parse(body);
      const created = TaskDetailResponseSchema.parse({
        ...aliceTask,
        id: request.id,
        title: request.title,
        description: request.description,
        clientId: request.clientId,
      });
      tasks = [...tasks, created];
      return json(created);
    },
    "memberships/list": () => json({ memberships: [] }),
  });
}

/** Панель создания задачи. */
const taskCreateSheet = async () =>
  within(await screen.findByRole("dialog", { name: ru["tasks.newTitle"] }));

describe("задачи в карточке клиента", () => {
  it("создаёт задачу для клиента в панели, не уходя со страницы", async () => {
    const api = tasksServer();
    const { history } = openApp(`/clients/${alice.id}?tab=tasks`, api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tasks.create"] }));

    const sheet = await taskCreateSheet();
    expect(sheet.getByLabelText(ru["tasks.field.client"])).toHaveTextContent(alice.name);
    expect(sheet.queryByRole("button", { name: /^Клиент/ })).toBeNull();
    expect(sheet.queryByRole("button", { name: ru["tasks.removeClient"] })).toBeNull();
    expect(history.location.pathname).toBe(`/clients/${alice.id}`);

    await user.type(sheet.getByLabelText(ru["tasks.field.title"]), "Отправить договор");
    await user.click(sheet.getByRole("button", { name: ru["action.create"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["tasks.newTitle"] })).toBeNull();
    });
    const request = CreateTaskRequestSchema.parse(api.to("tasks/create").at(-1)?.body);
    expect(request.clientId).toBe(alice.id);
    expect(await screen.findByRole("button", { name: "Отправить договор" })).toBeVisible();
    expect(history.location.pathname).toBe(`/clients/${alice.id}`);
    expect(history.location.search).toBe("?tab=tasks");
  });

  it("закрывает пустую форму создания без подтверждения", async () => {
    openApp(`/clients/${alice.id}?tab=tasks`, tasksServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["tasks.create"] }));
    await taskCreateSheet();
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["tasks.newTitle"] })).toBeNull();
    });
    expect(screen.queryByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeNull();
  });

  it("открывает карточку задачи поверх карточки клиента", async () => {
    const { history } = openApp(`/clients/${alice.id}?tab=tasks`, tasksServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: aliceTask.title }));

    const sheet = within(await screen.findByRole("dialog", { name: aliceTask.title }));
    expect(await sheet.findByText(aliceTask.description)).toBeVisible();
    expect(history.location.pathname).toBe(`/clients/${alice.id}`);
    expect(history.location.search).toBe("?tab=tasks");

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.getByRole("heading", { name: alice.name })).toBeVisible();
  });
});

/** Telegram-канал клиента, доступный для отправки на его телефон. */
const telegram = {
  id: "0199a0b2-7c3e-7d2a-9f10-000000000a01",
  name: "Бот школы",
  provider: "telegram_bot",
  available: true,
  contacts: [alice.contacts[0]],
};

/** Входящее сообщение от клиента. */
const greeting = MessageSchemaSchema.parse({
  type: "inbound",
  id: "0199a0b2-7c3e-7d2a-9f10-000000000b01",
  body: "Добрый день, можно перенести занятие?",
  createdAt: "2026-01-01T10:00:00Z",
  receivedVia: telegram.id,
});

/** Сервер карточки клиента с перепиской в памяти; отправка добавляет исходящее сообщение. */
function chatServer() {
  let messages: readonly MessageSchema[] = [greeting];
  return detailServer(alice, [], passport, {
    "messaging/conversation": () => json({ clientId: alice.id, messages }),
    "messaging/channels": () => json({ channels: [telegram] }),
    "messaging/send": ({ body }) => {
      const request = SendMessageRequestSchema.parse(body);
      messages = [
        ...messages,
        MessageSchemaSchema.parse({
          type: "outbound",
          id: "0199a0b2-7c3e-7d2a-9f10-000000000b02",
          body: request.body,
          createdAt: "2026-01-01T10:05:00Z",
          authorEmployeeId: null,
          deliveries: [{ channelIntegrationId: telegram.id, state: "SENT", errorMessage: null }],
        }),
      ];
      return json({ clientId: alice.id, messages });
    },
    "memberships/list": () => json({ memberships: [] }),
  });
}

/** Панель «Чат». */
const chatSheet = async () =>
  within(await screen.findByRole("dialog", { name: ru["messaging.title"] }));

/** Блок «Сообщения» карточки клиента. */
const messagesBlock = async () =>
  within(
    (await screen.findByRole("heading", { name: ru["clientCard.block.messages"] })).closest(
      "section",
    ) ?? document.body,
  );

describe("чат в карточке клиента", () => {
  it("открывает чат из шапки и отправляет сообщение, не уходя со страницы", async () => {
    const api = chatServer();
    const { history } = openApp(`/clients/${alice.id}`, api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["messaging.open"] }));

    const sheet = await chatSheet();
    expect(await sheet.findByText(greeting.body)).toBeVisible();
    const input = sheet.getByLabelText(ru["messaging.placeholder"]);
    await waitFor(() => {
      expect(input).toHaveFocus();
    });
    expect(history.location.pathname).toBe(`/clients/${alice.id}`);
    expect(history.location.search).toBe("");

    await user.type(input, "Да, конечно{Enter}");

    expect(await sheet.findByText("Да, конечно")).toBeVisible();
    expect(input).toHaveValue("");
    const request = SendMessageRequestSchema.parse(api.to("messaging/send").at(-1)?.body);
    expect(request).toMatchObject({ clientId: alice.id, channelIntegrationId: telegram.id });

    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect((await messagesBlock()).getByText("Да, конечно")).toBeVisible();
    expect(history.location.pathname).toBe(`/clients/${alice.id}`);
  });

  it("открывает чат из блока «Сообщения»", async () => {
    openApp(`/clients/${alice.id}`, chatServer().fetch);
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: ru["clientCard.openConversation"] }),
    );

    expect(await (await chatSheet()).findByText(greeting.body)).toBeVisible();
  });

  it("спрашивает подтверждение при закрытии с неотправленным текстом", async () => {
    openApp(`/clients/${alice.id}`, chatServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["messaging.open"] }));
    const sheet = await chatSheet();
    await user.type(await sheet.findByLabelText(ru["messaging.placeholder"]), "Черновик");
    await user.keyboard("{Escape}");

    const confirm = within(
      await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] }),
    );
    await user.click(confirm.getByRole("button", { name: ru["editSheet.discardConfirm"] }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["messaging.title"] })).toBeNull();
    });
  });

  it("закрывает чат без черновика без подтверждения", async () => {
    openApp(`/clients/${alice.id}`, chatServer().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["messaging.open"] }));
    await (await chatSheet()).findByText(greeting.body);
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["messaging.title"] })).toBeNull();
    });
    expect(screen.queryByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeNull();
  });
});

describe("одна страница и разделы карточки", () => {
  it("простой набор показывает анкету и контакты, не запрашивает выключенные разделы", async () => {
    const api = detailServer(alice, [], passport, {
      "org/client-card": () => json({ tasks: false, notes: false, chat: false, documents: false }),
    });
    const { history } = openApp(`/clients/${alice.id}?tab=notes`, api.fetch);
    await screen.findByRole("heading", { name: alice.name });
    expect(await screen.findByRole("heading", { name: ru["clientCard.profile"] })).toBeVisible();
    expect(history.location.search).toBe("?tab=notes");
    expect(screen.queryByRole("tablist")).toBeNull();
    expect(screen.queryByRole("button", { name: ru["messaging.open"] })).toBeNull();
    expect(api.to("tasks/list")).toHaveLength(0);
    expect(api.to("clients/notes/list")).toHaveLength(0);
    expect(api.to("messaging/conversation")).toHaveLength(0);
    expect(screen.queryByText(ru["clients.detail.noDocuments"])).toBeNull();
    const profile = screen
      .getByRole("heading", { name: ru["clientCard.profile"] })
      .closest<HTMLElement>("div.rounded-lg");
    expect(profile?.parentElement?.parentElement?.lastElementChild).toContainElement(profile);
  });
  it("игнорирует старый tab, visits открывает карточку без панели", async () => {
    const api = detailServer(alice);
    const { history } = openApp(`/clients/${alice.id}?tab=unknown`, api.fetch);
    await screen.findByRole("heading", { name: alice.name });
    expect(screen.queryByRole("tablist")).toBeNull();
    history.push(`/clients/${alice.id}/visits`);
    await waitFor(() => {
      expect(history.location.pathname).toBe(`/clients/${alice.id}`);
      expect(history.location.search).toBe("");
    });
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  it("сохранение разделов из меню включает карточку и сохраняет данные", async () => {
    let enabled = false;
    const api = detailServer(alice, [], passport, {
      "org/client-card": () =>
        json({ tasks: enabled, notes: false, chat: false, documents: false }),
      "org/client-card/update": () => {
        enabled = true;
        return json({ tasks: true, notes: false, chat: false, documents: false });
      },
    });
    openApp(`/clients/${alice.id}`, api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["clientCard.action.more"] }));
    await user.click(await screen.findByRole("menuitem", { name: ru["clientOverview.sections"] }));
    const sheet = within(
      await screen.findByRole("dialog", { name: ru["clientOverview.sections"] }),
    );
    await user.click(await sheet.findByRole("checkbox", { name: /Задачи/ }));
    await user.click(sheet.getByRole("button", { name: ru["action.save"] }));
    expect(await screen.findByRole("heading", { name: ru["tasks.title"] })).toBeVisible();
    expect(api.to("org/client-card/update")[0]?.body).toEqual({
      tasks: true,
      notes: false,
      chat: false,
      documents: false,
    });
  });
});

it("opens sessions with the keyboard and restores focus", async () => {
  const { history } = openApp(`/clients/${alice.id}`, detailServer(alice).fetch);
  const user = userEvent.setup();
  const attendance = await screen.findByRole("button", { name: ru["clientOverview.openSessions"] });
  attendance.focus();
  expect(attendance.className).toContain("focus-visible");
  expect(attendance).toHaveAttribute("aria-haspopup", "dialog");
  await user.keyboard("{Enter}");
  expect(await screen.findByRole("dialog", { name: ru["clientOverview.sessions"] })).toBeVisible();
  expect(history.location.search).toBe("");
  await user.keyboard("{Escape}");
  await waitFor(() => {
    expect(attendance).toHaveFocus();
  });
});

it.each(["escape", "close", "outside"])(
  "protects note drafts when closing via %s",
  async (method) => {
    openApp(`/clients/${alice.id}`, detailServer(alice).fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: /^Все заметки/ }));
    const panel = await screen.findByRole("dialog", { name: ru["clients.detail.notes"] });
    const field = within(panel).getByLabelText(ru["clients.detail.noteFieldLabel"]);
    await user.type(field, "Черновик заметки");
    if (method === "escape") await user.keyboard("{Escape}");
    else if (method === "close")
      await user.click(within(panel).getByRole("button", { name: ru["action.close"] }));
    else {
      const overlay = document.querySelector<HTMLElement>('[data-slot="sheet-overlay"]');
      if (overlay === null) throw new Error("Missing overlay");
      await user.click(overlay);
    }
    const confirm = within(
      await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] }),
    );
    await user.click(confirm.getByRole("button", { name: ru["action.cancel"] }));
    expect(field).toHaveValue("Черновик заметки");
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
  },
);

it("closes an empty list on outside click and restores focus", async () => {
  const { history } = openApp(`/clients/${alice.id}`, detailServer(alice).fetch);
  const user = userEvent.setup();
  const trigger = await screen.findByRole("button", { name: ru["clientOverview.openSessions"] });
  await user.click(trigger);
  await screen.findByRole("dialog", { name: ru["clientOverview.sessions"] });
  const overlay = document.querySelector<HTMLElement>('[data-slot="sheet-overlay"]');
  if (overlay === null) throw new Error("Missing overlay");
  await user.click(overlay);
  await waitFor(() => {
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  expect(trigger).toHaveFocus();
  expect(history.location.search).toBe("");
});

it("shows the creation date, age and full section layout", async () => {
  openApp(`/clients/${alice.id}`, detailServer(alice).fetch);
  const heading = await screen.findByRole("heading", { name: ru["clientCard.profile"] });
  const profile = heading.closest<HTMLElement>("div.rounded-lg");
  if (profile === null) throw new Error("Missing profile");
  expect(within(profile).getByText(ru["clientCard.createdAt"])).toBeVisible();
  expect(within(profile).getByText(/2024/)).toBeVisible();
  expect(within(profile).getByText(/1990.*лет/)).toBeVisible();
  await screen.findByRole("heading", { name: ru["tasks.title"] });
  const column = profile.parentElement?.parentElement;
  expect(column?.previousElementSibling).toBeNull();
  expect(within(profile).getByText(ru["clients.detail.noDocuments"])).toBeVisible();
  expect(screen.queryByRole("tablist")).toBeNull();
});

it("loads tasks beyond the first 50, keeps the list under a task, and refreshes counts", async () => {
  let tasks = Array.from({ length: 80 }, (_, index) =>
    TaskDetailResponseSchema.parse({
      ...aliceTask,
      id: TaskIdSchema.parse(
        `0199a0b2-7c3e-7d2a-9f10-${(index + 1).toString(16).padStart(12, "0")}`,
      ),
      title: `Задача ${String(index + 1)}`,
      status: index < 60 ? "PENDING" : "COMPLETED",
      dueDate: index >= 48 && index < 60 ? "2020-01-01T00:00:00Z" : null,
    }),
  );
  const api = detailServer(alice, [], passport, {
    "tasks/list": ({ body }) => {
      const request = TaskListRequestSchema.parse(body);
      const filtered = tasks.filter(
        (task) =>
          (request.statuses === undefined || request.statuses.includes(task.status)) &&
          (request.dueDateTo == null ||
            (task.dueDate !== null && task.dueDate <= request.dueDateTo)),
      );
      const offset = request.offset ?? 0;
      return json({
        tasks: filtered.slice(offset, offset + (request.limit ?? 50)),
        total: filtered.length,
      });
    },
    "tasks/detail": ({ body }) =>
      json(tasks.find((task) => task.id === TaskDetailRequestSchema.parse(body).taskId)),
    "tasks/status": ({ body }) => {
      const request = UpdateTaskStatusRequestSchema.parse(body);
      tasks = tasks.map((task) =>
        request.taskIds.includes(task.id) ? { ...task, status: request.status } : task,
      );
      return json({ updated: request.taskIds.length });
    },
  });
  const { history } = openApp(`/clients/${alice.id}`, api.fetch);
  const user = userEvent.setup();
  await screen.findByText(/12 просроченных задач/);
  const card = (await screen.findByRole("heading", { name: ru["tasks.title"] })).closest("section");
  if (card === null) throw new Error("Missing task card");
  await waitFor(() => {
    expect(within(card).getByRole("heading")).toHaveTextContent("60");
  });
  await user.click(within(card).getByRole("button", { name: /Все задачи · 80/ }));
  const list = await screen.findByRole("dialog", { name: ru["tasks.title"] });
  await user.click(await within(list).findByRole("button", { name: ru["clients.loadMore"] }));
  const taskRow = await within(list).findByRole("button", { name: "Задача 56" });
  await user.click(taskRow);
  const task = await screen.findByRole("dialog", { name: "Задача 56" });
  await user.click(within(task).getByRole("button", { name: ru["tasks.changeStatus"] }));
  await user.click(await screen.findByRole("menuitem", { name: ru["taskStatus.COMPLETED"] }));
  await user.keyboard("{Escape}");
  await waitFor(() => {
    expect(taskRow).toHaveFocus();
  });
  expect(list).toBeVisible();
  expect(within(list).getByRole("button", { name: "Задача 56" })).toBeVisible();
  await user.keyboard("{Escape}");
  await waitFor(() => {
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  await screen.findByText(/11 просроченных задач/);
  expect(within(card).getByRole("heading")).toHaveTextContent("59");
  expect(history.location.search).toBe("");
});

it("keeps an edited note and blocks closing while a note is being sent", async () => {
  let release: (() => void) | undefined;
  const waiting = new Promise<void>((resolve) => {
    release = resolve;
  });
  const note: ClientNoteSchema = {
    id: ClientNoteIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000702"),
    text: "Старый текст",
    author: { id: session().employeeId, name: session().name },
    createdAt: InstantSchema.parse("2024-01-01T10:00:00Z"),
    updatedAt: null,
  };
  const api = detailServer(alice, [note], passport, {
    "clients/notes/edit": () => json({ notes: [{ ...note, text: "Новый текст" }] }),
  });
  openApp(`/clients/${alice.id}`, async (input, init) => {
    if (new URL(input, "http://localhost").pathname === "/api/clients/notes/edit") await waiting;
    return api.fetch(input, init);
  });
  const user = userEvent.setup();
  await user.click(await screen.findByRole("button", { name: /^Все заметки/ }));
  const panel = await screen.findByRole("dialog", { name: ru["clients.detail.notes"] });
  await user.click(
    await within(panel).findByRole("button", { name: ru["clients.detail.noteEditAria"] }),
  );
  const field = within(panel).getByLabelText(ru["clients.detail.noteFieldLabel"]);
  await user.clear(field);
  await user.type(field, "Новый текст");
  await user.keyboard("{Escape}");
  const confirm = within(await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] }));
  await user.click(confirm.getByRole("button", { name: ru["action.cancel"] }));
  expect(field).toHaveValue("Новый текст");
  await user.click(within(panel).getByRole("button", { name: ru["action.save"] }));
  await user.keyboard("{Escape}");
  expect(panel).toBeVisible();
  expect(screen.queryByRole("dialog", { name: ru["editSheet.discardTitle"] })).toBeNull();
  release?.();
  await waitFor(() => {
    expect(field).toHaveValue("");
  });
  await user.keyboard("{Escape}");
  await waitFor(() => {
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
