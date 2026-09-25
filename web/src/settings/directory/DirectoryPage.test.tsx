import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import {
  CreateHallRequestSchema,
  DeleteHallRequestSchema,
  UpdateHallRequestSchema,
} from "@/api/generated/contracts";
import { ru } from "@/i18n/ru";
import { appServer, center, empty, json, openApp } from "@/test/app";

const bigHall = { id: "0199a0b2-7c3e-7d2a-9f10-000000000101", name: "Большой зал" };
const smallHall = { id: "0199a0b2-7c3e-7d2a-9f10-000000000102", name: "Малый зал" };

/** Сервер со справочником залов в памяти: создание, переименование и удаление меняют список. */
function hallsServer() {
  let list = [bigHall, smallHall];
  return appServer({
    "halls/list": () => json({ halls: list }),
    "halls/create": ({ body }) => {
      list = [...list, CreateHallRequestSchema.parse(body)];
      return empty();
    },
    "halls/update": ({ body }) => {
      const hall = UpdateHallRequestSchema.parse(body);
      list = list.map((h) => (h.id === hall.id ? hall : h));
      return empty();
    },
    "halls/delete": ({ body }) => {
      const { ids } = DeleteHallRequestSchema.parse(body);
      list = list.filter((h) => !ids.some((id) => id === h.id));
      return empty();
    },
  });
}

describe("справочник", () => {
  it("создаёт запись с новым UUID v7 и показывает её в списке", async () => {
    const api = hallsServer();
    openApp("/settings/halls", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["action.add"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["halls.create"] });
    await user.type(within(dialog).getByLabelText(ru["directory.name"]), "  Зал для йоги ");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("button", { name: "Изменить «Зал для йоги»" })).toBeVisible();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    const [created] = api.to("halls/create").map((r) => CreateHallRequestSchema.parse(r.body));
    expect(created?.name).toBe("Зал для йоги");
    expect(created?.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7/);
  });

  it("пустое название не отправляется", async () => {
    const api = hallsServer();
    openApp("/settings/halls", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["action.add"] }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await within(dialog).findByText(ru["error.required"])).toBeVisible();
    expect(api.to("halls/create")).toHaveLength(0);
  });

  it("переименовывает запись", async () => {
    const api = hallsServer();
    openApp("/settings/halls", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить «Малый зал»" }));
    const dialog = await screen.findByRole("dialog", { name: ru["halls.edit"] });
    const name = within(dialog).getByLabelText(ru["directory.name"]);
    expect(name).toHaveValue("Малый зал");
    await user.clear(name);
    await user.type(name, "Зал №2");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByRole("button", { name: "Изменить «Зал №2»" })).toBeVisible();
    expect(api.to("halls/update").map((r) => r.body)).toEqual([
      { id: smallHall.id, name: "Зал №2" },
    ]);
  });

  it("ошибка сервера остаётся в диалоге", async () => {
    const api = appServer({
      "halls/list": () => json({ halls: [bigHall] }),
      "halls/update": () => json({ code: "DUPLICATE", message: "Зал уже есть", fields: null }, 409),
    });
    openApp("/settings/halls", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Изменить «Большой зал»" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Зал уже есть");
  });

  it("удаляет выбранные записи после подтверждения", async () => {
    const api = hallsServer();
    openApp("/settings/halls", api.fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("checkbox", { name: "Выбрать «Большой зал»" }));
    expect(screen.getByText("Выбрано: 1")).toBeVisible();
    await user.click(screen.getByRole("button", { name: ru["directory.deleteSelected"] }));
    const dialog = await screen.findByRole("dialog", { name: "Удалить 1 запись?" });
    await user.click(within(dialog).getByRole("button", { name: ru["action.delete"] }));

    await waitFor(() => {
      expect(screen.queryByText("Большой зал")).not.toBeInTheDocument();
    });
    expect(screen.getByText("Малый зал")).toBeVisible();
    expect(api.to("halls/delete").map((r) => r.body)).toEqual([{ ids: [bigHall.id] }]);
    expect(screen.queryByText(/Выбрано/)).not.toBeInTheDocument();
  });

  it("поиск фильтрует список, «Выбрать все» выбирает только найденное", async () => {
    const api = hallsServer();
    openApp("/settings/halls", api.fetch);
    const user = userEvent.setup();

    await user.type(await screen.findByRole("searchbox"), "мал");
    expect(screen.queryByText("Большой зал")).not.toBeInTheDocument();
    await user.click(screen.getByRole("checkbox", { name: ru["directory.selectAll"] }));
    expect(screen.getByText("Выбрано: 1")).toBeVisible();

    await user.clear(screen.getByRole("searchbox"));
    await user.type(screen.getByRole("searchbox"), "нет такого");
    expect(screen.getByText(ru["directory.noResults"])).toBeVisible();
  });

  it("дисциплины", async () => {
    const api = appServer({
      "disciplines/list": () => json({ disciplines: [{ id: bigHall.id, name: "Бокс" }] }),
    });
    openApp("/settings/disciplines", api.fetch);
    expect(await screen.findByRole("heading", { name: ru["disciplines.title"] })).toBeVisible();
    expect(await screen.findByText("Бокс")).toBeVisible();
  });

  it("источники клиентов", async () => {
    const api = appServer({
      "lead-sources/list": () => json({ leadSources: [{ id: bigHall.id, name: "Instagram" }] }),
    });
    openApp("/settings/client-sources", api.fetch);
    expect(await screen.findByRole("heading", { name: ru["leadSources.title"] })).toBeVisible();
    expect(await screen.findByText("Instagram")).toBeVisible();
  });

  it("новый филиал сразу доступен для переключения в меню аккаунта", async () => {
    const north = { id: "0199a0b2-7c3e-7d2a-9f10-000000000104", name: "Север" };
    let list = [center];
    const api = appServer({
      "branches/list": () => json({ branches: list }),
      "auth/my-branches": () => json({ branches: list }),
      "branches/create": () => {
        list = [center, north];
        return empty();
      },
    });
    openApp("/settings/branches", api.fetch);
    const user = userEvent.setup();
    await user.click(await screen.findByRole("button", { name: ru["account.menu"] }));
    expect(await screen.findByText(ru["account.currentBranch"])).toBeVisible();
    expect(screen.queryByRole("menuitem", { name: /Центр/ })).not.toBeInTheDocument();
    await user.keyboard("{Escape}");

    await user.click(await screen.findByRole("button", { name: ru["action.add"] }));
    const dialog = await screen.findByRole("dialog", { name: ru["branches.create"] });
    await user.type(within(dialog).getByLabelText(ru["directory.name"]), "Север");
    await user.click(within(dialog).getByRole("button", { name: ru["action.save"] }));

    expect(await screen.findByText("Север")).toBeVisible();
    await user.click(screen.getByRole("button", { name: ru["account.menu"] }));
    expect(
      await screen.findByRole("menuitem", { name: `${ru["account.switchBranch"]}: Центр` }),
    ).toBeVisible();
  });
});
