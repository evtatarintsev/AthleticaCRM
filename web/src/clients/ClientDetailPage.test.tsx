import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  AddClientNoteRequestSchema,
  AttachClientDocRequestSchema,
  ClientDetailResponseSchema,
  ClientNoteIdSchema,
  CustomFieldDefinitionSchema,
  DeleteClientDocRequestSchema,
  DeleteClientNoteRequestSchema,
  EditClientNoteRequestSchema,
  InstantSchema,
  type ClientDetailResponse,
  type ClientNoteSchema,
  type CustomFieldDefinition,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, empty, json, openApp, session } from "@/test/app";

const alice = ClientDetailResponseSchema.parse({
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
function detailServer(initial: ClientDetailResponse, notes: readonly ClientNoteSchema[] = []) {
  let client = initial;
  let currentNotes = notes;
  return appServer({
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
    upload: () =>
      json({
        id: "0199a0b2-7c3e-7d2a-9f10-000000000801",
        url: "https://files.example.com/passport.pdf",
        originalName: "passport.pdf",
        contentType: "application/pdf",
        sizeBytes: 1234,
      }),
    "upload/info": () =>
      json({
        id: "0199a0b2-7c3e-7d2a-9f10-000000000801",
        url: "https://files.example.com/passport.pdf",
        originalName: "passport.pdf",
        contentType: "application/pdf",
        sizeBytes: 1234,
      }),
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
    "clients/docs/delete": ({ body }) => {
      const request = DeleteClientDocRequestSchema.parse(body);
      client = { ...client, docs: client.docs.filter((doc) => doc.id !== request.docId) };
      return empty();
    },
  });
}

describe("карточка клиента", () => {
  it("показывает сведения, добавляет заметку и переносит клиента в архив", async () => {
    const api = detailServer(alice);
    openApp(`/clients/${alice.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByRole("heading", { name: alice.name });
    expect(screen.getByText(alice.contacts[0]?.value ?? "")).toBeInTheDocument();
    expect(screen.getByText(alice.contacts[1]?.value ?? "")).toBeInTheDocument();
    expect(screen.getByText("Утренняя группа")).toBeInTheDocument();

    await user.type(
      screen.getByLabelText(ru["clients.detail.noteFieldLabel"]),
      "Перезвонить завтра",
    );
    await user.click(screen.getByRole("button", { name: ru["action.add"] }));

    await screen.findByText("Перезвонить завтра");
    const addRequest = AddClientNoteRequestSchema.parse(api.to("clients/notes/add").at(-1)?.body);
    expect(addRequest.clientId).toBe(alice.id);
    expect(addRequest.text).toBe("Перезвонить завтра");

    await user.click(screen.getByRole("button", { name: ru["action.archive"] }));
    await screen.findByText(ru["clients.detail.archivedBanner"]);
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
    openApp(`/clients/${alice.id}`, api.fetch);
    const user = userEvent.setup();

    await screen.findByText("исходный текст");
    await user.click(screen.getByRole("button", { name: ru["clients.detail.noteEditAria"] }));
    const field = screen.getByLabelText(ru["clients.detail.noteFieldLabel"]);
    await user.clear(field);
    await user.type(field, "новый текст");
    await user.click(screen.getByRole("button", { name: ru["action.save"] }));

    await screen.findByText("новый текст");
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

    await screen.findByText(ru["clients.detail.noDocuments"]);
    const fileInput = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (fileInput === null) {
      throw new Error("file input not found");
    }
    const file = new File(["content"], "passport.pdf", { type: "application/pdf" });
    await user.upload(fileInput, file);

    const docLink = await screen.findByRole("link", { name: "passport.pdf" });
    expect(docLink).toHaveAttribute("href", "https://files.example.com/passport.pdf");

    await user.click(
      screen.getByRole("button", {
        name: ru["clients.detail.deleteDoc"].replace("{name}", "passport.pdf"),
      }),
    );
    await user.click(screen.getByRole("button", { name: ru["action.delete"] }));

    await waitFor(() => {
      expect(screen.getByText(ru["clients.detail.noDocuments"])).toBeInTheDocument();
    });
  });

  it("показывает дополнительные поля клиента", async () => {
    const withField = ClientDetailResponseSchema.parse({
      ...alice,
      customFields: [{ type: "text", fieldKey: "notes", value: "аллергия на пыль" }],
    });
    const api = detailServer(withField);
    openApp(`/clients/${withField.id}`, api.fetch);

    await screen.findByText("аллергия на пыль");
    const section = screen.getByText(ru["clients.additionalFields"]).closest("div");
    if (section === null) {
      throw new Error("section not found");
    }
    expect(within(section).getByText("Заметка")).toBeInTheDocument();
  });
});
