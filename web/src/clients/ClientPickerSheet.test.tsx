import { QueryClientProvider } from "@tanstack/react-query";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "@/api/client";
import {
  BranchIdSchema,
  ClientListItemSchema,
  ClientListRequestSchema,
  type ClientId,
  type ClientListItem,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { createQueryClient } from "@/query/queries";
import { fakeServer, json } from "@/test/app";
import { renderPage } from "@/test/render";
import { ClientPickerSheet, type ClientPickerMode, type PickedClient } from "./ClientPickerSheet";
import { CLIENTS_PAGE_SIZE } from "./clientsQueries";

const branchId = BranchIdSchema.parse("0199a0b2-7c3e-7d2a-9f10-000000000003");

/** Клиент с порядковым номером [n] и именем [name]. */
const client = (n: number, name: string) =>
  ClientListItemSchema.parse({
    id: `0199a0b2-0000-7000-8000-${String(n).padStart(12, "0")}`,
    name,
    avatarId: null,
    birthday: null,
    gender: "MALE",
    groups: [],
    balance: { minorUnits: 0, currency: "RUB" },
    customFields: [],
    contacts: [],
    state: "ACTIVE",
  });

const ivan = client(1, "Иван Смирнов");
const petr = client(2, "Пётр Иванов");
const olga = client(3, "Ольга Петрова");

/** Поддельный `clients/list`: поиск по вхождению без учёта регистра и порции по `offset`. */
function clientsServer(all: readonly ClientListItem[]) {
  return fakeServer({
    "clients/list": ({ body }) => {
      const request = ClientListRequestSchema.parse(body);
      const name = request.name?.toLowerCase() ?? null;
      const found = all.filter((c) => name === null || c.name.toLowerCase().includes(name));
      const offset = request.offset ?? 0;
      return json({
        clients: found.slice(offset, offset + (request.limit ?? found.length)),
        total: found.length,
      });
    },
  });
}

/**
 * Открывает панель выбора в режиме [mode] поверх сервера [server]; [onSubmit] — обработка
 * выбора, [unavailable] — недоступные клиенты.
 */
function openPicker({
  server,
  mode,
  onSubmit = () => Promise.resolve(null),
  unavailable = new Map(),
}: {
  server: ReturnType<typeof clientsServer>;
  mode: ClientPickerMode;
  onSubmit?: (clients: readonly PickedClient[]) => Promise<string | null>;
  unavailable?: ReadonlyMap<ClientId, string>;
}) {
  const api = createApiClient({
    fetch: server.fetch,
    language: () => "ru",
    onSessionExpired: () => undefined,
    reportContractViolation: () => undefined,
  });
  const queryClient = createQueryClient();

  /** Экран, держащий панель открытой, пока она сама не попросит закрыться. */
  function Harness() {
    const [open, setOpen] = useState(true);
    return (
      <ClientPickerSheet
        api={api}
        branchId={branchId}
        open={open}
        onOpenChange={setOpen}
        title="Выбор клиента"
        mode={mode}
        unavailable={unavailable}
        submitLabel="Добавить"
        onSubmit={onSubmit}
      />
    );
  }

  renderPage(
    <QueryClientProvider client={queryClient}>
      <Harness />
    </QueryClientProvider>,
  );
}

/** Запросы списка клиентов, разобранные по схеме. */
const listRequests = (server: ReturnType<typeof clientsServer>) =>
  server.to("clients/list").map((r) => ClientListRequestSchema.parse(r.body));

describe("панель выбора клиента", () => {
  it("ищет по имени одним запросом после паузы ввода", async () => {
    const server = clientsServer([ivan, petr, olga]);
    openPicker({ server, mode: "multiple" });
    const user = userEvent.setup();

    await screen.findByText(olga.name);
    await user.type(screen.getByRole("searchbox", { name: ru["clientPicker.search"] }), "иван");

    await waitFor(() => {
      expect(screen.queryByText(olga.name)).not.toBeInTheDocument();
    });
    expect(screen.getByText(ivan.name)).toBeVisible();
    expect(screen.getByText(petr.name)).toBeVisible();
    expect(listRequests(server).map((r) => r.name ?? null)).toEqual([null, "иван"]);
  });

  it("показывает сообщение, если никто не найден", async () => {
    openPicker({ server: clientsServer([ivan]), mode: "multiple" });
    const user = userEvent.setup();

    await screen.findByText(ivan.name);
    await user.type(screen.getByRole("searchbox"), "zzz");

    expect(await screen.findByText(ru["clientPicker.empty"])).toBeVisible();
  });

  it("догружает следующую порцию по «Показать ещё»", async () => {
    const all = Array.from({ length: CLIENTS_PAGE_SIZE + 1 }, (_, i) =>
      client(i + 10, `Клиент ${String(i + 1).padStart(3, "0")}`),
    );
    const server = clientsServer(all);
    openPicker({ server, mode: "multiple" });
    const user = userEvent.setup();

    await screen.findByText("Клиент 050");
    expect(screen.queryByText("Клиент 051")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: ru["clients.loadMore"] }));

    expect(await screen.findByText("Клиент 051")).toBeVisible();
    expect(screen.queryByRole("button", { name: ru["clients.loadMore"] })).not.toBeInTheDocument();
    expect(listRequests(server).at(-1)?.offset).toBe(CLIENTS_PAGE_SIZE);
  });

  it("недоступного клиента показывает с причиной и не даёт отметить", async () => {
    openPicker({
      server: clientsServer([ivan, petr]),
      mode: "multiple",
      unavailable: new Map([[ivan.id, "Уже в группе"]]),
    });

    const checkbox = await screen.findByRole("checkbox", { name: /Иван Смирнов/ });
    expect(checkbox).toBeDisabled();
    expect(screen.getByText("Уже в группе")).toBeVisible();
    expect(screen.getByRole("checkbox", { name: petr.name })).toBeEnabled();
  });

  it("сохраняет отметки при смене поиска и передаёт всех отмеченных", async () => {
    const onSubmit = vi.fn<(clients: readonly PickedClient[]) => Promise<string | null>>(() =>
      Promise.resolve(null),
    );
    openPicker({ server: clientsServer([ivan, petr, olga]), mode: "multiple", onSubmit });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: olga.name }));
    await user.type(screen.getByRole("searchbox"), "смир");
    await waitFor(() => {
      expect(screen.queryByText(olga.name)).not.toBeInTheDocument();
    });
    await user.click(screen.getByRole("checkbox", { name: ivan.name }));

    expect(screen.getByText(ru["directory.selected"].replace("{count}", "2"))).toBeVisible();
    await user.click(screen.getByRole("button", { name: "Добавить" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(onSubmit.mock.calls[0]?.[0]).toEqual([
      { id: olga.id, name: olga.name },
      { id: ivan.id, name: ivan.name },
    ]);
  });

  it("при ошибке оставляет панель открытой с прежними отметками", async () => {
    openPicker({
      server: clientsServer([ivan]),
      mode: "multiple",
      onSubmit: () => Promise.resolve("Группа в архиве"),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: ivan.name }));
    await user.click(screen.getByRole("button", { name: "Добавить" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Группа в архиве");
    expect(screen.getByRole("dialog")).toBeVisible();
    expect(screen.getByRole("checkbox", { name: ivan.name })).toBeChecked();
  });

  it("в одиночном режиме выбирает клиента нажатием и закрывается", async () => {
    const onSubmit = vi.fn<(clients: readonly PickedClient[]) => Promise<string | null>>(() =>
      Promise.resolve(null),
    );
    openPicker({ server: clientsServer([ivan, petr]), mode: "single", onSubmit });
    const user = userEvent.setup();

    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: petr.name }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(onSubmit.mock.calls[0]?.[0]).toEqual([{ id: petr.id, name: petr.name }]);
  });

  it("в одиночном режиме при ошибке остаётся открытой и показывает её", async () => {
    openPicker({
      server: clientsServer([ivan]),
      mode: "single",
      onSubmit: () => Promise.resolve("Занятие отменено"),
    });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ivan.name }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Занятие отменено");
    expect(screen.getByRole("button", { name: ivan.name })).toBeEnabled();
  });

  it("при закрытии с отметками спрашивает подтверждение", async () => {
    openPicker({ server: clientsServer([ivan]), mode: "multiple" });
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: ivan.name }));
    await user.keyboard("{Escape}");

    const confirm = await screen.findByRole("dialog", { name: ru["editSheet.discardTitle"] });
    await user.click(within(confirm).getByRole("button", { name: ru["editSheet.discardConfirm"] }));
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("без отметок закрывается сразу", async () => {
    openPicker({ server: clientsServer([ivan]), mode: "multiple" });
    const user = userEvent.setup();

    await screen.findByText(ivan.name);
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(screen.queryByText(ru["editSheet.discardTitle"])).not.toBeInTheDocument();
  });
});
