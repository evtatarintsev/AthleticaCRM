import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ru } from "@/i18n/ru";
import { appServer, center, json, openApp, session } from "@/test/app";

const north = { id: "0199a0b2-7c3e-7d2a-9f10-000000000004", name: "Северный" };
const lenin = { id: "0199a0b2-7c3e-7d2a-9f10-000000000005", name: "На Ленинском" };
const kids = { id: "0199a0b2-7c3e-7d2a-9f10-000000000006", name: "Детский центр" };

/** Подпись карточки-кнопки клуба «Лига» с текущим филиалом [branch]. */
function cardLabel(branch: string): string {
  return ru["clubCard.label"].replace("{org}", "Лига").replace("{branch}", branch);
}

/** Сервер с четырьмя доступными филиалами; переключение меняет филиал сессии. */
function fourBranches() {
  let branch = center;
  return appServer({
    "auth/me": () => json(session("Иван Петров", null, branch)),
    "auth/my-branches": () => json({ branches: [center, north, lenin, kids] }),
    "auth/switch-branch": () => {
      branch = north;
      return json({ accessToken: "", refreshToken: "" });
    },
  });
}

/** Боковая панель навигации компьютерной раскладки. */
async function sidebar() {
  return within(await screen.findByRole("complementary"));
}

/** Верхняя полоса раскладки. */
async function topBar() {
  return within(await screen.findByRole("banner"));
}

describe("карточка клуба", () => {
  it("при одном филиале — подпись с монограммой, без кнопки", async () => {
    const api = appServer({});
    openApp("/", api.fetch);

    expect(await (await sidebar()).findByText("Лига")).toBeInTheDocument();
    expect((await sidebar()).getByText("Центр")).toBeInTheDocument();
    expect((await sidebar()).getByText("Л")).toHaveAttribute("aria-hidden", "true");
    await waitFor(() => {
      expect(api.to("auth/my-branches")).toHaveLength(1);
    });
    expect(screen.queryByRole("button", { name: cardLabel("Центр") })).not.toBeInTheDocument();
  });

  it("при четырёх филиалах открывает список с шапкой, отметкой текущего и управлением", async () => {
    openApp("/", fourBranches().fetch);
    const user = userEvent.setup();

    await user.click(await (await sidebar()).findByRole("button", { name: cardLabel("Центр") }));

    const menu = await screen.findByRole("menu");
    expect(within(menu).getByText("4 филиала")).toBeInTheDocument();
    expect(within(menu).getAllByRole("menuitemradio")).toHaveLength(4);
    expect(within(menu).getByRole("menuitemradio", { name: "Центр" })).toBeChecked();
    expect(
      within(menu).getByRole("menuitem", { name: ru["clubCard.manageBranches"] }),
    ).toHaveAttribute("href", "/settings/branches");
  });

  it("выбор другого филиала переключает сессию и меняет карточку", async () => {
    const api = fourBranches();
    openApp("/", api.fetch);
    const user = userEvent.setup();

    await user.click(await (await sidebar()).findByRole("button", { name: cardLabel("Центр") }));
    await user.click(await screen.findByRole("menuitemradio", { name: "Северный" }));

    expect(
      await (await sidebar()).findByRole("button", { name: cardLabel("Северный") }),
    ).toBeVisible();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    expect(api.to("auth/switch-branch").map((r) => r.body)).toEqual([{ branchId: north.id }]);
  });

  it("выбор текущего филиала только закрывает список", async () => {
    const api = fourBranches();
    openApp("/", api.fetch);
    const user = userEvent.setup();

    await user.click(await (await sidebar()).findByRole("button", { name: cardLabel("Центр") }));
    await user.click(await screen.findByRole("menuitemradio", { name: "Центр" }));

    await waitFor(() => {
      expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });
    expect(api.to("auth/switch-branch")).toHaveLength(0);
  });

  it("с клавиатуры: Enter открывает, стрелки ходят по филиалам, Escape возвращает фокус", async () => {
    openApp("/", fourBranches().fetch);
    const user = userEvent.setup();

    const card = await (await sidebar()).findByRole("button", { name: cardLabel("Центр") });
    card.focus();
    await user.keyboard("{Enter}");
    await screen.findByRole("menu");
    await user.keyboard("{ArrowDown}");
    expect(screen.getByRole("menuitemradio", { name: "Северный" })).toHaveFocus();
    await user.keyboard("{Escape}");

    await waitFor(() => {
      expect(screen.queryByRole("menu")).not.toBeInTheDocument();
    });
    expect(card).toHaveFocus();
  });

  it("на телефоне открывает шторку, выбор филиала закрывает её и переключает", async () => {
    const api = fourBranches();
    openApp("/", api.fetch);
    const user = userEvent.setup();

    await user.click(await (await topBar()).findByRole("button", { name: cardLabel("Центр") }));
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).getByText("4 филиала")).toBeInTheDocument();
    expect(within(sheet).getByRole("button", { name: "Центр" })).toHaveAttribute(
      "aria-current",
      "true",
    );
    expect(within(sheet).getByRole("link", { name: ru["clubCard.manageBranches"] })).toBeVisible();
    await user.click(within(sheet).getByRole("button", { name: "Северный" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
    expect(api.to("auth/switch-branch").map((r) => r.body)).toEqual([{ branchId: north.id }]);
  });

  it("стоит в выезжающем меню, а логотип продукта не получает фокус", async () => {
    openApp("/", fourBranches().fetch);
    const user = userEvent.setup();

    const logo = await (await sidebar()).findByRole("img", { name: ru["app.name"] });
    expect(logo).toHaveAttribute("src", "/logo.png");
    expect(logo.closest("a, button")).toBeNull();
    await user.click(screen.getByRole("button", { name: ru["nav.openMenu"] }));
    const drawer = await screen.findByRole("dialog", { name: ru["nav.label"] });
    expect(within(drawer).getByRole("button", { name: cardLabel("Центр") })).toBeVisible();
    expect(
      within(drawer).getByRole("img", { name: ru["app.name"] }).closest("a, button"),
    ).toBeNull();
  });
});

describe("меню аккаунта", () => {
  it("не показывает текущий филиал и смену филиала", async () => {
    openApp("/", fourBranches().fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["account.menu"] }));
    const menu = await screen.findByRole("menu");

    expect(within(menu).queryByText("Центр")).not.toBeInTheDocument();
    expect(within(menu).queryByRole("menuitemradio", { name: "Северный" })).not.toBeInTheDocument();
  });

  it("первым пунктом открывает помощника и возвращает фокус на кнопку меню", async () => {
    const api = appServer({
      "assistant/status": () => json({ available: true, enabled: true, permitted: true }),
      "assistant/dialogs/current": () =>
        json({ id: "0199a0b2-7c3e-7d2a-9f10-000000000012", messages: [] }),
    });
    openApp("/", api.fetch);
    const user = userEvent.setup();

    const account = await screen.findByRole("button", { name: ru["account.menu"] });
    await screen.findByRole("button", { name: ru["assistant.title"] });
    await user.click(account);
    const items = within(await screen.findByRole("menu")).getAllByRole("menuitem");
    expect(items[0]).toHaveTextContent(ru["assistant.title"]);
    await user.click(items[0] ?? account);

    expect(await screen.findByRole("dialog", { name: ru["assistant.title"] })).toBeVisible();
    await user.keyboard("{Escape}");
    await waitFor(() => {
      expect(screen.queryByRole("dialog", { name: ru["assistant.title"] })).not.toBeInTheDocument();
    });
    expect(account).toHaveFocus();
  });

  it("без доступа к помощнику пункта нет", async () => {
    openApp("/", appServer({}).fetch);
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: ru["account.menu"] }));
    const menu = await screen.findByRole("menu");

    expect(within(menu).queryByText(ru["assistant.title"])).not.toBeInTheDocument();
  });
});
